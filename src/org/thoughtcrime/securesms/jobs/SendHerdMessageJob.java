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
import org.spongycastle.crypto.macs.HMac;
import org.spongycastle.jcajce.provider.digest.SHA256;
import org.spongycastle.jcajce.provider.symmetric.AES;
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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import javax.crypto.KeyGenerator;
import javax.crypto.spec.SecretKeySpec;
import javax.inject.Inject;

import static java.lang.Math.abs;
import static org.thoughtcrime.securesms.dependencies.SignalCommunicationModule.SignalMessageSenderFactory;

public class SendHerdMessageJob extends PushSendJob implements InjectableType {

    public static final int TYPE_HANDSHAKE_REQUEST = 0;
    public static final int TYPE_HANDSHAKE_RESPONSE = 1;
    public static final int TYPE_MESSAGE = 2;

    public static final int TYPE_PSI_SYN = 3;
    public static final int TYPE_PSI_SYN_ACK = 4;
    public static final int TYPE_PSI_ACK = 5;

    private static final Object HERD_LOCK = new Object();

    private static final ReentrantLock PSI_CLIENT_LOCK = new ReentrantLock();
    private static final ReentrantLock PSI_SERVER_LOCK = new ReentrantLock();
    private static Crypto.PrivateSetIntersection mServerPSI;
    private static Crypto.PrivateSetIntersection mClientPSI;

    private static final int PSI_SYN_SET_SIZE = 10;

    public static long previousRun = 0;

    private static final long serialVersionUID = 1L;

    private String TAG = SendHerdMessageJob.class.getSimpleName();

    @Inject
    transient SignalMessageSenderFactory messageSenderFactory;

    private HerdProtos.HandshakeMessage herdHandshakeMessage = null;
    private int messageType;
    private long messageID;
    private String mobyId;

    private String destination = null;

    public SendHerdMessageJob(Context context, String destination, int messageType) {
        super(context, constructParameters(context, destination));
        this.destination = destination;
        this.messageType = messageType;
    }

    public SendHerdMessageJob(Context context, String destination, int messageType, HerdProtos.HandshakeMessage handshakeMessage) {
        super(context, constructParameters(context, destination));
        this.destination = destination;
        this.messageType = messageType;
        this.herdHandshakeMessage = handshakeMessage;
    }

    public SendHerdMessageJob(Context context, long messageID, String destination, int messageType) {
        super(context, constructParameters(context, destination));
        this.messageID = messageID;
        this.destination = destination;
        this.messageType = messageType;
    }

    public SendHerdMessageJob(Context context, String destination, int messageType, String mobyId) {
        super(context, constructParameters(context, destination));

        this.destination = destination;
        this.messageType = messageType;
        this.mobyId = mobyId;

        FriendStore friendStore = FriendStore.getInstance(context);

        if (messageType == TYPE_HANDSHAKE_REQUEST) {
            // Adding Friend to FriendStore so that we don't keep spamming them :)
            // In case it's a request, if not, we'd overwrite the value!
            friendStore.addFriend("", FriendStore.ADDED_VIA_HERD_HANDSHAKE, destination, null);
            this.herdHandshakeMessage = HerdProtos.HandshakeMessage.newBuilder()
                    .setPublicDevieID(friendStore.getPublicDeviceIDString(context, StorageBase.ENCRYPTION_DEFAULT))
                    .setMessageType(this.messageType).build();
        } else if (messageType == TYPE_HANDSHAKE_RESPONSE) {
            // Generate a shard secret for that user and add it to the Friend store.
            try {
                KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA256");
                byte[] sharedSecret = keyGenerator.generateKey().getEncoded();
                friendStore.addFriend(mobyId, FriendStore.ADDED_VIA_HERD_HANDSHAKE, destination, sharedSecret);
                this.herdHandshakeMessage = HerdProtos.HandshakeMessage.newBuilder()
                        .setPublicDevieID(friendStore.getPublicDeviceIDString(context, StorageBase.ENCRYPTION_DEFAULT))
                        .setMessageType(this.messageType)
                        .setSharedSecret(ByteString.copyFrom(sharedSecret)).build();

            } catch (NoSuchAlgorithmException e) {
                Log.d(TAG, e.getMessage());
            }
        }

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
        HerdProtos.ClientMessage clientMessage = null;
        HerdProtos.ServerMessage serverMessage = null;
        HerdProtos.HandshakeMessage newHandshakeMessage = null;
        Crypto.PrivateSetIntersection.ServerReplyTuple serverReplyTuple = null;
        ArrayList<ByteString> doubleBlindedFriends = null;
        ArrayList<ByteString> hashedBlindedFriends = null;
        ArrayList<ByteString> friendsProtos = null;
        ArrayList<byte[]> friends = null;
        ArrayList<byte[]> hashes = null;
        int commonFriends = -1;
        switch (this.messageType) {
            case TYPE_MESSAGE:
                Log.d(TAG, "Trying to send a local herd message!!");
                deliverMessage(masterSecret);
                break;
            case TYPE_HANDSHAKE_REQUEST:
            case TYPE_HANDSHAKE_RESPONSE:
                try {
                    Thread.sleep(calculateSleep());
                    deliverHandshake();
                } catch (InsecureFallbackApprovalException | UntrustedIdentityException | InterruptedException e) {
                    // This should rarely happen, we accept the failure in such circumstances.
                    Log.w(TAG, e);
                }
                break;

            case TYPE_PSI_SYN:
                Log.d(TAG, "Sending PSI_SYN to: " + destination);
                friends = getNFriendsBytes(PSI_SYN_SET_SIZE);
                try {
                    mClientPSI = new Crypto.PrivateSetIntersection(friends);
                } catch (NoSuchAlgorithmException e) {
                    Log.w(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                }
                friendsProtos = new ArrayList<>();
                for (byte[] friend : friends) {
                    friendsProtos.add(ByteString.copyFrom(friend));
                }
                clientMessage = HerdProtos.ClientMessage.newBuilder()
                        .addAllBlindedFriends(friendsProtos)
                        .build();
                newHandshakeMessage = HerdProtos.HandshakeMessage.newBuilder()
                        .setMessageType(messageType)
                        .setClientMessage(clientMessage)
                        .build();
                try {
                    deliverPSIMessage(newHandshakeMessage);
                } catch (InsecureFallbackApprovalException | UntrustedIdentityException e) {
                    // This should rarely happen, we accept the failure in such circumstances.
                    Log.w(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                }
                break;

            case TYPE_PSI_SYN_ACK:
                Log.d(TAG, "Trying to send a SYN_ACK from outside SYN handler, how is this possible o.O");
                break;

            case TYPE_PSI_ACK:
                Log.d(TAG, "Trying to send an ACK from outside SYN_ACK handler, how is this possible o.O");
                break;


            // Responding to the corresponding messages by doing something, PushDecryptJob schedules
            // these based on whatever it receives and parses.
            case -TYPE_PSI_SYN:
                Log.d(TAG, "Handling SYN from: " + destination);
                // do client stuff to set the client message field.
                friends = getNFriendsBytes(this.herdHandshakeMessage.getClientMessage().getSetSize());
                try {
                    mClientPSI = new Crypto.PrivateSetIntersection(friends);
                    mServerPSI = new Crypto.PrivateSetIntersection(friends);
                } catch (NoSuchAlgorithmException e) {
                    Log.d(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                    return;
                }
                friendsProtos = new ArrayList<>();
                for (byte[] friend : friends) {
                    friendsProtos.add(ByteString.copyFrom(friend));
                }
                clientMessage = HerdProtos.ClientMessage.newBuilder()
                        .setSetSize(this.herdHandshakeMessage.getClientMessage().getSetSize())
                        .addAllBlindedFriends(friendsProtos)
                        .build();
                List<ByteString> blindedFriendsList = this.herdHandshakeMessage.getClientMessage().getBlindedFriendsList();
                ArrayList<byte[]> blindedFriendsListBytes = new ArrayList<>();
                for (ByteString blindedFriend : blindedFriendsList) {
                    blindedFriendsListBytes.add(blindedFriend.toByteArray());
                }
                try {
                    serverReplyTuple = mServerPSI.replyToBlindedItems(blindedFriendsListBytes);
                    for (byte[] element : serverReplyTuple.doubleBlindedItems) {
                        doubleBlindedFriends.add(ByteString.copyFrom(element));
                    }
                    for (byte[] element : serverReplyTuple.hashedBlindedItems) {
                        hashedBlindedFriends.add(ByteString.copyFrom(element));
                    }
                    serverMessage = HerdProtos.ServerMessage.newBuilder()
                            .addAllDoubleBlindedFriends(doubleBlindedFriends)
                            .addAllHashedBlindedFriends(hashedBlindedFriends)
                            .build();
                } catch (NoSuchAlgorithmException e) {
                    Log.d(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                    return;
                }

                newHandshakeMessage = HerdProtos.HandshakeMessage.newBuilder()
                        .setMessageType(TYPE_PSI_SYN_ACK)
                        .setClientMessage(clientMessage)
                        .setServerMessage(serverMessage)
                        .build();
                try {
                    deliverPSIMessage(newHandshakeMessage);
                } catch (InsecureFallbackApprovalException | UntrustedIdentityException e) {
                    // This should rarely happen, we accept the failure in such circumstances.
                    Log.w(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                }
                Log.d(TAG, "Done handling SYN from: " + destination);
                break;

            case -TYPE_PSI_SYN_ACK:
                Log.d(TAG, "Handling SYN_ACK from: " + destination);
                friends = new ArrayList<>();
                hashes = new ArrayList<>();
                for (ByteString element : this.herdHandshakeMessage.getServerMessage().getDoubleBlindedFriendsList())
                    friends.add(element.toByteArray());
                for (ByteString element : this.herdHandshakeMessage.getServerMessage().getHashedBlindedFriendsList())
                    hashes.add(element.toByteArray());
                serverReplyTuple = mClientPSI.new ServerReplyTuple(friends, hashes);
                try {
                    commonFriends = mClientPSI.getCardinality(serverReplyTuple);
                    Log.d(TAG, "Completed SYN_ACK, found friends in common: " + commonFriends);
                } catch (Exception e) {
                    Log.w(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                }

                try {
                    friends = getNFriendsBytes(this.herdHandshakeMessage.getClientMessage().getSetSize());
                    mServerPSI = new Crypto.PrivateSetIntersection(friends);

                    friends = new ArrayList<>();
                    doubleBlindedFriends = new ArrayList<>();
                    hashedBlindedFriends = new ArrayList<>();

                    for(ByteString element : this.herdHandshakeMessage.getClientMessage().getBlindedFriendsList())
                        friends.add(element.toByteArray());

                    serverReplyTuple = mServerPSI.replyToBlindedItems(friends);
                    for (byte[] element : serverReplyTuple.doubleBlindedItems) {
                        doubleBlindedFriends.add(ByteString.copyFrom(element));
                    }
                    for (byte[] element : serverReplyTuple.hashedBlindedItems) {
                        hashedBlindedFriends.add(ByteString.copyFrom(element));
                    }
                    serverMessage = HerdProtos.ServerMessage.newBuilder()
                            .addAllDoubleBlindedFriends(doubleBlindedFriends)
                            .addAllHashedBlindedFriends(hashedBlindedFriends)
                            .build();
                } catch (NoSuchAlgorithmException e) {
                    Log.d(TAG, "PSI Failed at step: " + messageType + e.getMessage());

                }
                newHandshakeMessage = HerdProtos.HandshakeMessage.newBuilder()
                        .setMessageType(TYPE_PSI_ACK)
                        .setServerMessage(serverMessage)
                        .build();
                try {
                    deliverPSIMessage(newHandshakeMessage);
                } catch (InsecureFallbackApprovalException | UntrustedIdentityException e) {
                    // This should rarely happen, we accept the failure in such circumstances.
                    Log.w(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                }
                Log.d(TAG, "Done handling SYN_ACK from: " + destination);
                break;

            case -TYPE_PSI_ACK:
                Log.d(TAG, "Handling ACK from: " + destination);
                friends = new ArrayList<>();
                hashes = new ArrayList<>();
                for (ByteString element : this.herdHandshakeMessage.getServerMessage().getDoubleBlindedFriendsList())
                    friends.add(element.toByteArray());
                for (ByteString element : this.herdHandshakeMessage.getServerMessage().getHashedBlindedFriendsList())
                    hashes.add(element.toByteArray());
                serverReplyTuple = mClientPSI.new ServerReplyTuple(friends, hashes);
                try {
                    commonFriends = mClientPSI.getCardinality(serverReplyTuple);
                    Log.d(TAG, "Completed ACK, found friends in common: " + commonFriends);
                } catch (Exception e) {
                    Log.w(TAG, "PSI Failed at step: " + messageType + e.getMessage());
                }
                Log.d(TAG, "Done handling ACK from: " + destination);
                break;
        }
    }

    private ArrayList<byte[]> getNFriendsBytes(int number) {
        Random r = new Random();
        ArrayList<byte[]> byteArrays = new ArrayList<>();
        for (int i = 0; i < number; i++) {
            byte[] element = new byte[16];
            r.nextBytes(element);
            byteArrays.add(element);
        }
        return byteArrays;
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

    private void deliverPSIMessage(HerdProtos.HandshakeMessage handshakeMessage)
            throws UntrustedIdentityException, InsecureFallbackApprovalException {
        try {
            Log.d(TAG, "PSI With: " + this.destination + " at step: " + this.messageType);
            SignalServiceAddress address = getPushAddress(this.destination);
            SignalServiceMessageSender messageSender = messageSenderFactory.create();

            SignalServiceDataMessage textSecureMessage = SignalServiceDataMessage.newBuilder()
                    .withBody(Base64.encodeBytes(handshakeMessage.toByteArray()))
                    .withExpiration(0)
                    .asEndSessionMessage(false)
                    .build();
            messageSender.sendHerdMessage(address, textSecureMessage);
        } catch (AuthorizationFailedException | RateLimitException | InvalidNumberException | UnregisteredUserException e) {
            // Theoretically, none of these are possible when we're trying to trigger a PSI exchange!
            Log.e(TAG, e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private void deliverMessage(MasterSecret masterSecret) {
        EncryptingSmsDatabase database = DatabaseFactory.getEncryptingSmsDatabase(context);
        try {
            SmsMessageRecord record = database.getMessage(masterSecret, this.messageID);
            FriendStore friendStore = FriendStore.getInstance(context);
            SignalServiceProtos.Content.Builder container = SignalServiceProtos.Content.newBuilder();
            SignalServiceProtos.DataMessage.Builder builder = SignalServiceProtos.DataMessage.newBuilder();

            String body = record.getBody().getBody();
            // TODO amoghbl1: Need to check that messages aren't longer than 140 to start with.
            // Trying to add null padding here to see if it still works or gets stripped.
            while (body.length() < 140) {
                body += " ";
            }
            builder.setBody(body);

            byte[] content = container.setDataMessage(builder).build().toByteArray();

            SignalServiceAddress address = getPushAddress(record.getIndividualRecipient().getNumber());
            SignalServiceMessageSender messageSender = messageSenderFactory.create();
            OutgoingPushMessage opm = messageSender.getEncryptedMessageAssumingSession(address, content, false);

            MessageStore messageStore = MessageStore.getInstance(context);

            long timestamp = System.currentTimeMillis();
            String destinationNumber = record.getRecipients().getPrimaryRecipient().getNumber().replaceAll("\\s", "");

            String payload = opm.getContent();
            String mobyTag = friendStore.generateMobyTag(destinationNumber, payload);

            // Used to debug if our tags work. Commented for now.
            // String sender = friendStore.verifyMobyTag(mobyTag, payload);
            // Log.d(TAG, "MobyTag testing: " + sender + " Destination: " + destinationNumber);

            Log.d(TAG, "Sending timestamp: " + timestamp + " encrypted message: " + payload);
            if (destination == null) {
                Log.d(TAG, "Don't seem to have the friends key in the store, how did we try to send a herd message o.O");
                return;
            }
            long ttl = 259200000; // 72 hour constant for now.
            messageStore.addMessage(timestamp, timestamp + ttl, mobyTag, payload);

            ExchangeHistoryTracker.getInstance(context).cleanHistory(null);
            MessageStore.getInstance(context).updateStoreVersion();

            // Marking message as sent, but well, who knows :P
            database.markAsSent(this.messageID, true);
        } catch (InvalidNumberException e) {
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

            if (this.herdHandshakeMessage == null) {
                Log.e(TAG, "HOW DO WE HAVE A NULL HANDSHAKE MESSAGE :O");
                return;
            }

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
                    .add(new SendHerdMessageJob(context, this.destination, this.messageType, this.mobyId));
            return;
        } catch (RateLimitException e) {
            Log.w(TAG, e);
            // We wait for a minute when we hit the RateLimitException.
            updateTime(60000);
            // We're gonna add another job to the queue and let it come around, instead of looping on deliver()
            ApplicationContext.getInstance(context)
                    .getJobManager()
                    .add(new SendHerdMessageJob(context, this.destination, this.messageType, this.mobyId));
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