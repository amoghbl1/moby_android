package org.thoughtcrime.securesms.jobs;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.google.protobuf.ByteString;
import com.klinker.android.send_message.Utils;

import org.denovogroup.murmur.backend.Crypto;
import org.denovogroup.murmur.backend.ExchangeHistoryTracker;
import org.denovogroup.murmur.backend.MessageStore;
import org.denovogroup.murmur.backend.Peer;
import org.denovogroup.murmur.backend.SecurityProfile;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.MasterSecret;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.EncryptingSmsDatabase;
import org.thoughtcrime.securesms.database.NoSuchMessageException;
import org.thoughtcrime.securesms.database.TextSecureDirectory;
import org.thoughtcrime.securesms.database.model.SmsMessageRecord;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.denovogroup.murmur.backend.FriendStore;
import org.denovogroup.murmur.backend.SecurityManager;
import org.denovogroup.murmur.backend.StorageBase;
import org.denovogroup.murmur.objects.HerdProtos;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.transport.InsecureFallbackApprovalException;
import org.thoughtcrime.securesms.transport.RetryLaterException;
import org.thoughtcrime.securesms.util.Base64;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.InvalidNumberException;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.Envelope;
import org.whispersystems.signalservice.internal.util.JsonUtil;

import java.io.IOException;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import static java.lang.Math.abs;
import static org.thoughtcrime.securesms.dependencies.SignalCommunicationModule.SignalMessageSenderFactory;

public class SendHerdMessageJob extends PushSendJob implements InjectableType {

    public static final int TYPE_HANDSHAKE_REQUEST = 0;
    public static final int TYPE_HANDSHAKE_RESPONSE = 1;
    public static final int TYPE_MESSAGE = 2;

    public static final Object HERD_LOCK = new Object();
    public static long previousRun = 0;

    private static final long serialVersionUID = 1L;

    private String TAG = SendHerdMessageJob.class.getSimpleName();

    @Inject
    transient SignalMessageSenderFactory messageSenderFactory;

    private HerdProtos.HandshakeMessage herdHandshakeMessage;
    private int messageType;
    private long messageID;

    private String destination = null;

    public SendHerdMessageJob(Context context, long messageID, String destination, int messageType) {
        super(context, constructParameters(context, destination));
        this.messageID = messageID;
        this.destination = destination;
        this.messageType = messageType;
    }

    public SendHerdMessageJob(Context context, String destination, int messageType) {
        super(context, constructParameters(context, destination));

        this.destination = destination;
        this.messageType = messageType;

        FriendStore friendStore = FriendStore.getInstance(context);
        this.herdHandshakeMessage = HerdProtos.HandshakeMessage.newBuilder()
                .setPublicDevieID(friendStore.getPublicDeviceIDString(context, StorageBase.ENCRYPTION_DEFAULT))
                .setMessageType(this.messageType).build();
        // Improves log readability
        TAG += ": " + this.destination;
    }

    // Calculate the amount a thread needs to sleep, based on how past
    private synchronized long calculateSleep() {
        synchronized (HERD_LOCK) {
            Long now = System.currentTimeMillis();
            if (now > previousRun + 20000) {
                this.previousRun = System.currentTimeMillis();
                return 0;
            }
            return abs(now - previousRun);
        }
    }

    private synchronized void updateTime(long extra) {
        synchronized (HERD_LOCK) {
            this.previousRun = System.currentTimeMillis() + extra;
        }
    }

    @Override
    public void onAdded() {
    }

    @Override
    public void onPushSend(MasterSecret masterSecret) throws NoSuchMessageException {
        if (this.messageType == this.TYPE_MESSAGE) {
            Log.d(TAG, "Trying to send a local herd message!!");
            deliverMessage(masterSecret);
        } else {
            try {
                Thread.sleep(calculateSleep());
                deliverHandshake();
            } catch (InsecureFallbackApprovalException e) {
                Log.w(TAG, e);
            } catch (UntrustedIdentityException e) {
                Log.w(TAG, e);
            } catch (InterruptedException e) {
                // This should rarely happen, we accept the failure in such circumstances.
                Log.w(TAG, e);
            }
        }
    }

    @Override
    public boolean onShouldRetryThrowable(Exception exception) {
        if (exception instanceof RetryLaterException) return true;

        return false;
    }

    @Override
    public void onCanceled() {
        // Do something to reschedule a HerdHandshakeJob, not handling this failure right now.
    }

    protected SignalServiceAddress getPushAddress(String number) throws InvalidNumberException {
        String e164number = Util.canonicalizeNumber(context, number);
        String relay = TextSecureDirectory.getInstance(context).getRelay(e164number);
        return new SignalServiceAddress(e164number, Optional.fromNullable(relay));
    }

    private void deliverMessage(MasterSecret masterSecret) {
        EncryptingSmsDatabase database    = DatabaseFactory.getEncryptingSmsDatabase(context);
        try {
            SmsMessageRecord      record      = database.getMessage(masterSecret, this.messageID);

            SignalServiceProtos.Content.Builder         container = SignalServiceProtos.Content.newBuilder();
            SignalServiceProtos.DataMessage.Builder     builder   = SignalServiceProtos.DataMessage.newBuilder();

            builder.setBody(record.getBody().getBody());
            byte[] content = container.setDataMessage(builder).build().toByteArray();

            SignalServiceAddress address = getPushAddress(record.getIndividualRecipient().getNumber());
            SignalServiceMessageSender messageSender = messageSenderFactory.create();
            OutgoingPushMessage opm = messageSender.getEncryptedMessageAssumingSession(address, content, false);

            MessageStore messageStore = MessageStore.getInstance(context);


            long   timestamp   = System.currentTimeMillis();
            String destination = record.getRecipients().getPrimaryRecipient().getNumber().replaceAll("\\s", "");
            String source      = TextSecurePreferences.getLocalNumber(context);
            String payload     =  ByteString.copyFrom(JsonUtil.toJson(opm).getBytes()).toStringUtf8();

            Log.d(TAG, "Sending timestamp: " + timestamp + " encrypted message: " + payload);
            messageStore.addMessage(timestamp, source, destination, payload);

            ExchangeHistoryTracker.getInstance(context).cleanHistory(null);
            MessageStore.getInstance(context).updateStoreVersion();

            // Marking message as sent, but well, who knows :P
            database.markAsSent(this.messageID, true);
        } catch (InvalidNumberException  e) {
            Log.e(TAG, e.getMessage());
        } catch (NoSuchMessageException e) {
            Log.e(TAG, e.getMessage());
        } catch (UntrustedIdentityException e) {
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void deliverHandshake()
            throws UntrustedIdentityException, InsecureFallbackApprovalException {
        try {
            Log.d(TAG, "Trying to deliver to: " + this.destination);
            SignalServiceAddress address = getPushAddress(this.destination);
            SignalServiceMessageSender messageSender = messageSenderFactory.create();

            SignalServiceDataMessage textSecureMessage = SignalServiceDataMessage.newBuilder()
                    //.withTimestamp(now)
                    .withBody(Base64.encodeBytes(this.herdHandshakeMessage.toByteArray()))
                    .withExpiration(0)
                    .asEndSessionMessage(false)
                    .build();
            messageSender.sendHerdMessage(address, textSecureMessage);
        } catch (AuthorizationFailedException e) {
            Log.w(TAG, e);
            // We wait for a minute when we hit the AuthorizationFailedException.
            updateTime(60000);
            // We're gonna add another job to the queue and let it come around, instead of looping on deliver()
            ApplicationContext.getInstance(context)
                    .getJobManager()
                    .add(new SendHerdMessageJob(context, this.destination, this.messageType));
            return;
        } catch (RateLimitException e) {
            Log.w(TAG, e);
            // We wait for a minute when we hit the RateLimitException.
            updateTime(60000);
            // We're gonna add another job to the queue and let it come around, instead of looping on deliver()
            ApplicationContext.getInstance(context)
                    .getJobManager()
                    .add(new SendHerdMessageJob(context, this.destination, this.messageType));
            return;
        } catch (InvalidNumberException | UnregisteredUserException e) {
            Log.w(TAG, e);
            // We don't care if the user isn't registered, he can't possibly make sense of a
            // Herd Handshake then :P
        } catch (IOException e) {
            Log.w(TAG, e);
            // We're not gonna retry this later, just make sure that we do the handshake on this thread.
            // Might not be the best idea, maybe we should hadle this the way PushTextSendJob does.
        }
        updateTime(10000);
    }
}