/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.internal.push;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.ecc.ECPublicKey;
import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.libsignal.state.PreKeyBundle;
import org.whispersystems.libsignal.state.PreKeyRecord;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.libsignal.util.Pair;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.crypto.AttachmentCipherOutputStream;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment.ProgressListener;
import org.whispersystems.signalservice.api.messages.calls.TurnServerInfo;
import org.whispersystems.signalservice.api.messages.multidevice.DeviceInfo;
import org.whispersystems.signalservice.api.push.ContactTokenDetails;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.SignalServiceProfile;
import org.whispersystems.signalservice.api.push.SignedPreKeyEntity;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.util.Base64;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.ConnectionSpec;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * @author Moxie Marlinspike
 */
public class PushServiceSocket {

  private static final String TAG = PushServiceSocket.class.getSimpleName();

  private static final String CREATE_ACCOUNT_SMS_PATH   = "/v1/accounts/sms/code/%s";
  private static final String CREATE_ACCOUNT_VOICE_PATH = "/v1/accounts/voice/code/%s";
  private static final String VERIFY_ACCOUNT_CODE_PATH  = "/v1/accounts/code/%s";
  private static final String REGISTER_GCM_PATH         = "/v1/accounts/gcm/";
  private static final String REQUEST_TOKEN_PATH        = "/v1/accounts/token";
  private static final String TURN_SERVER_INFO          = "/v1/accounts/turn";
  private static final String SET_ACCOUNT_ATTRIBUTES    = "/v1/accounts/attributes/";

  private static final String PREKEY_METADATA_PATH      = "/v2/keys/";
  private static final String PREKEY_PATH               = "/v2/keys/%s";
  private static final String PREKEY_DEVICE_PATH        = "/v2/keys/%s/%s";
  private static final String SIGNED_PREKEY_PATH        = "/v2/keys/signed";

  private static final String PROVISIONING_CODE_PATH    = "/v1/devices/provisioning/code";
  private static final String PROVISIONING_MESSAGE_PATH = "/v1/provisioning/%s";
  private static final String DEVICE_PATH               = "/v1/devices/%s";

  private static final String DIRECTORY_TOKENS_PATH     = "/v1/directory/tokens";
  private static final String DIRECTORY_VERIFY_PATH     = "/v1/directory/%s";
  private static final String MESSAGE_PATH              = "/v1/messages/%s";
  private static final String ACKNOWLEDGE_MESSAGE_PATH  = "/v1/messages/%s/%d";
  private static final String RECEIPT_PATH              = "/v1/receipt/%s/%d";
  private static final String ATTACHMENT_PATH           = "/v1/attachments/%s";

  private static final String PROFILE_PATH              = "/v1/profile/%s";

  private       long      soTimeoutMillis = TimeUnit.SECONDS.toMillis(30);
  private final Set<Call> connections     = new HashSet<>();

  private final SignalConnectionInformation[] signalConnectionInformation;
  private final CredentialsProvider           credentialsProvider;
  private final String                        userAgent;
  private final SecureRandom                  random;

  public PushServiceSocket(SignalServiceUrl[] serviceUrls, CredentialsProvider credentialsProvider, String userAgent) {
    try {
      this.credentialsProvider         = credentialsProvider;
      this.userAgent                   = userAgent;
      this.signalConnectionInformation = new SignalConnectionInformation[serviceUrls.length];
      this.random                      = SecureRandom.getInstance("SHA1PRNG");

      for (int i = 0; i < serviceUrls.length; i++) {
        signalConnectionInformation[i] = new SignalConnectionInformation(serviceUrls[i]);
      }
    } catch (NoSuchAlgorithmException e) {
      throw new AssertionError(e);
    }
  }

  public void createAccount(boolean voice) throws IOException {
    String path = voice ? CREATE_ACCOUNT_VOICE_PATH : CREATE_ACCOUNT_SMS_PATH;
    makeRequest(String.format(path, credentialsProvider.getUser()), "GET", null);
  }

  public void verifyAccountCode(String verificationCode, String signalingKey, int registrationId, boolean fetchesMessages)
      throws IOException
  {
    AccountAttributes signalingKeyEntity = new AccountAttributes(signalingKey, registrationId, fetchesMessages);
    makeRequest(String.format(VERIFY_ACCOUNT_CODE_PATH, verificationCode),
                "PUT", JsonUtil.toJson(signalingKeyEntity));
  }

  public void setAccountAttributes(String signalingKey, int registrationId, boolean fetchesMessages) throws IOException {
    AccountAttributes accountAttributes = new AccountAttributes(signalingKey, registrationId, fetchesMessages);
    makeRequest(SET_ACCOUNT_ATTRIBUTES, "PUT", JsonUtil.toJson(accountAttributes));
  }

  public String getAccountVerificationToken() throws IOException {
    String responseText = makeRequest(REQUEST_TOKEN_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, AuthorizationToken.class).getToken();
  }

  public String getNewDeviceVerificationCode() throws IOException {
    String responseText = makeRequest(PROVISIONING_CODE_PATH, "GET", null);
    return JsonUtil.fromJson(responseText, DeviceCode.class).getVerificationCode();
  }

  public List<DeviceInfo> getDevices() throws IOException {
    String responseText = makeRequest(String.format(DEVICE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, DeviceInfoList.class).getDevices();
  }

  public void removeDevice(long deviceId) throws IOException {
    makeRequest(String.format(DEVICE_PATH, String.valueOf(deviceId)), "DELETE", null);
  }

  public void sendProvisioningMessage(String destination, byte[] body) throws IOException {
    makeRequest(String.format(PROVISIONING_MESSAGE_PATH, destination), "PUT",
                JsonUtil.toJson(new ProvisioningMessage(Base64.encodeBytes(body))));
  }

  public void sendReceipt(String destination, long messageId, Optional<String> relay) throws IOException {
    String path = String.format(RECEIPT_PATH, destination, messageId);

    if (relay.isPresent()) {
      path += "?relay=" + relay.get();
    }

    makeRequest(path, "PUT", null);
  }

  public void registerGcmId(String gcmRegistrationId) throws IOException {
    GcmRegistrationId registration = new GcmRegistrationId(gcmRegistrationId, true);
    makeRequest(REGISTER_GCM_PATH, "PUT", JsonUtil.toJson(registration));
  }

  public void unregisterGcmId() throws IOException {
    makeRequest(REGISTER_GCM_PATH, "DELETE", null);
  }

  public SendMessageResponse sendMessage(OutgoingPushMessageList bundle)
      throws IOException
  {
    try {
      String responseText = makeRequest(String.format(MESSAGE_PATH, bundle.getDestination()), "PUT", JsonUtil.toJson(bundle));

      if (responseText == null) return new SendMessageResponse(false);
      else                      return JsonUtil.fromJson(responseText, SendMessageResponse.class);
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(bundle.getDestination(), nfe);
    }
  }

  public List<SignalServiceEnvelopeEntity> getMessages() throws IOException {
    String responseText = makeRequest(String.format(MESSAGE_PATH, ""), "GET", null);
    return JsonUtil.fromJson(responseText, SignalServiceEnvelopeEntityList.class).getMessages();
  }

  public void acknowledgeMessage(String sender, long timestamp) throws IOException {
    makeRequest(String.format(ACKNOWLEDGE_MESSAGE_PATH, sender, timestamp), "DELETE", null);
  }

  public void registerPreKeys(IdentityKey identityKey,
                              SignedPreKeyRecord signedPreKey,
                              List<PreKeyRecord> records)
      throws IOException
  {
    List<PreKeyEntity> entities = new LinkedList<>();

    for (PreKeyRecord record : records) {
      PreKeyEntity entity = new PreKeyEntity(record.getId(),
                                             record.getKeyPair().getPublicKey());

      entities.add(entity);
    }

    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());

    makeRequest(String.format(PREKEY_PATH, ""), "PUT",
                JsonUtil.toJson(new PreKeyState(entities, signedPreKeyEntity, identityKey)));
  }

  public int getAvailablePreKeys() throws IOException {
    String       responseText = makeRequest(PREKEY_METADATA_PATH, "GET", null);
    PreKeyStatus preKeyStatus = JsonUtil.fromJson(responseText, PreKeyStatus.class);

    return preKeyStatus.getCount();
  }

  public List<PreKeyBundle> getPreKeys(SignalServiceAddress destination, int deviceIdInteger) throws IOException {
    try {
      String deviceId = String.valueOf(deviceIdInteger);

      if (deviceId.equals("1"))
        deviceId = "*";

      String path = String.format(PREKEY_DEVICE_PATH, destination.getNumber(), deviceId);

      if (destination.getRelay().isPresent()) {
        path = path + "?relay=" + destination.getRelay().get();
      }

      String             responseText = makeRequest(path, "GET", null);
      PreKeyResponse     response     = JsonUtil.fromJson(responseText, PreKeyResponse.class);
      List<PreKeyBundle> bundles      = new LinkedList<>();

      for (PreKeyResponseItem device : response.getDevices()) {
        ECPublicKey preKey                = null;
        ECPublicKey signedPreKey          = null;
        byte[]      signedPreKeySignature = null;
        int         preKeyId              = -1;
        int         signedPreKeyId        = -1;

        if (device.getSignedPreKey() != null) {
          signedPreKey          = device.getSignedPreKey().getPublicKey();
          signedPreKeyId        = device.getSignedPreKey().getKeyId();
          signedPreKeySignature = device.getSignedPreKey().getSignature();
        }

        if (device.getPreKey() != null) {
          preKeyId = device.getPreKey().getKeyId();
          preKey   = device.getPreKey().getPublicKey();
        }

        bundles.add(new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId,
                                     preKey, signedPreKeyId, signedPreKey, signedPreKeySignature,
                                     response.getIdentityKey()));
      }

      return bundles;
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getNumber(), nfe);
    }
  }

  public PreKeyBundle getPreKey(SignalServiceAddress destination, int deviceId) throws IOException {
    try {
      String path = String.format(PREKEY_DEVICE_PATH, destination.getNumber(),
                                  String.valueOf(deviceId));

      if (destination.getRelay().isPresent()) {
        path = path + "?relay=" + destination.getRelay().get();
      }

      String         responseText = makeRequest(path, "GET", null);
      PreKeyResponse response     = JsonUtil.fromJson(responseText, PreKeyResponse.class);

      if (response.getDevices() == null || response.getDevices().size() < 1)
        throw new IOException("Empty prekey list");

      PreKeyResponseItem device                = response.getDevices().get(0);
      ECPublicKey        preKey                = null;
      ECPublicKey        signedPreKey          = null;
      byte[]             signedPreKeySignature = null;
      int                preKeyId              = -1;
      int                signedPreKeyId        = -1;

      if (device.getPreKey() != null) {
        preKeyId = device.getPreKey().getKeyId();
        preKey   = device.getPreKey().getPublicKey();
      }

      if (device.getSignedPreKey() != null) {
        signedPreKeyId        = device.getSignedPreKey().getKeyId();
        signedPreKey          = device.getSignedPreKey().getPublicKey();
        signedPreKeySignature = device.getSignedPreKey().getSignature();
      }

      return new PreKeyBundle(device.getRegistrationId(), device.getDeviceId(), preKeyId, preKey,
                              signedPreKeyId, signedPreKey, signedPreKeySignature, response.getIdentityKey());
    } catch (NotFoundException nfe) {
      throw new UnregisteredUserException(destination.getNumber(), nfe);
    }
  }

  public SignedPreKeyEntity getCurrentSignedPreKey() throws IOException {
    try {
      String responseText = makeRequest(SIGNED_PREKEY_PATH, "GET", null);
      return JsonUtil.fromJson(responseText, SignedPreKeyEntity.class);
    } catch (NotFoundException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  public void setCurrentSignedPreKey(SignedPreKeyRecord signedPreKey) throws IOException {
    SignedPreKeyEntity signedPreKeyEntity = new SignedPreKeyEntity(signedPreKey.getId(),
                                                                   signedPreKey.getKeyPair().getPublicKey(),
                                                                   signedPreKey.getSignature());
    makeRequest(SIGNED_PREKEY_PATH, "PUT", JsonUtil.toJson(signedPreKeyEntity));
  }

  public Pair<Long, byte[]> sendAttachment(PushAttachmentData attachment) throws IOException {
    String               response      = makeRequest(String.format(ATTACHMENT_PATH, ""), "GET", null);
    AttachmentDescriptor attachmentKey = JsonUtil.fromJson(response, AttachmentDescriptor.class);

    if (attachmentKey == null || attachmentKey.getLocation() == null) {
      throw new IOException("Server failed to allocate an attachment key!");
    }

    Log.w(TAG, "Got attachment content location: " + attachmentKey.getLocation());

    byte[] digest = uploadAttachment("PUT", attachmentKey.getLocation(), attachment.getData(),
                                     attachment.getDataSize(), attachment.getKey(), attachment.getListener());

    return new Pair<>(attachmentKey.getId(), digest);
  }

  public void retrieveAttachment(String relay, long attachmentId, File destination, int maxSizeBytes, ProgressListener listener) throws IOException {
    String path = String.format(ATTACHMENT_PATH, String.valueOf(attachmentId));

    if (!Util.isEmpty(relay)) {
      path = path + "?relay=" + relay;
    }

    String               response   = makeRequest(path, "GET", null);
    AttachmentDescriptor descriptor = JsonUtil.fromJson(response, AttachmentDescriptor.class);

    Log.w(TAG, "Attachment: " + attachmentId + " is at: " + descriptor.getLocation());

    downloadExternalFile(descriptor.getLocation(), destination, maxSizeBytes, listener);
  }

  public SignalServiceProfile retrieveProfile(SignalServiceAddress target) throws
      NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      String response = makeRequest(String.format(PROFILE_PATH, target.getNumber()), "GET", null);
      return JsonUtil.fromJson(response, SignalServiceProfile.class);
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public List<ContactTokenDetails> retrieveDirectory(Set<String> contactTokens)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    try {
      ContactTokenList        contactTokenList = new ContactTokenList(new LinkedList<>(contactTokens));
      String                  response         = makeRequest(DIRECTORY_TOKENS_PATH, "PUT", JsonUtil.toJson(contactTokenList));
      ContactTokenDetailsList activeTokens     = JsonUtil.fromJson(response, ContactTokenDetailsList.class);

      return activeTokens.getContacts();
    } catch (IOException e) {
      Log.w(TAG, e);
      throw new NonSuccessfulResponseCodeException("Unable to parse entity");
    }
  }

  public ContactTokenDetails getContactTokenDetails(String contactToken) throws IOException {
    try {
      String response = makeRequest(String.format(DIRECTORY_VERIFY_PATH, contactToken), "GET", null);
      return JsonUtil.fromJson(response, ContactTokenDetails.class);
    } catch (NotFoundException nfe) {
      return null;
    }
  }

  public TurnServerInfo getTurnServerInfo() throws IOException {
    String response = makeRequest(TURN_SERVER_INFO, "GET", null);
    return JsonUtil.fromJson(response, TurnServerInfo.class);
  }

  public void setSoTimeoutMillis(long soTimeoutMillis) {
    this.soTimeoutMillis = soTimeoutMillis;
  }

  public void cancelInFlightRequests() {
    synchronized (connections) {
      Log.w(TAG, "Canceling: " + connections.size());
      for (Call connection : connections) {
        Log.w(TAG, "Canceling: " + connection);
        connection.cancel();
      }
    }
  }

  private void downloadExternalFile(String url, File localDestination, int maxSizeBytes, ProgressListener listener)
      throws IOException
  {
    URL               downloadUrl = new URL(url);
    HttpURLConnection connection  = (HttpURLConnection) downloadUrl.openConnection();
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.setRequestMethod("GET");
    connection.setDoInput(true);

    try {
      if (connection.getResponseCode() != 200) {
        throw new NonSuccessfulResponseCodeException("Bad response: " + connection.getResponseCode());
      }

      OutputStream output        = new FileOutputStream(localDestination);
      InputStream  input         = connection.getInputStream();
      byte[]       buffer        = new byte[4096];
      int          contentLength = connection.getContentLength();
      int         read,totalRead = 0;

      if (contentLength > maxSizeBytes) {
        throw new NonSuccessfulResponseCodeException("File exceeds maximum size.");
      }

      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
        totalRead += read;

        if (totalRead > maxSizeBytes) {
          localDestination.delete();
          throw new NonSuccessfulResponseCodeException("File exceeds maximum size.");
        }

        if (listener != null) {
          listener.onAttachmentProgress(contentLength, totalRead);
        }
      }

      output.close();
      Log.w(TAG, "Downloaded: " + url + " to: " + localDestination.getAbsolutePath());
    } catch (IOException ioe) {
      throw new PushNetworkException(ioe);
    } finally {
      connection.disconnect();
    }
  }

  private byte[] uploadAttachment(String method, String url, InputStream data,
                                  long dataSize, byte[] key, ProgressListener listener)
    throws IOException
  {
    URL                uploadUrl  = new URL(url);
    HttpsURLConnection connection = (HttpsURLConnection) uploadUrl.openConnection();
    connection.setDoOutput(true);

    if (dataSize > 0) {
      connection.setFixedLengthStreamingMode((int) AttachmentCipherOutputStream.getCiphertextLength(dataSize));
    } else {
      connection.setChunkedStreamingMode(0);
    }

    connection.setRequestMethod(method);
    connection.setRequestProperty("Content-Type", "application/octet-stream");
    connection.setRequestProperty("Connection", "close");
    connection.connect();

    try {
      OutputStream                 stream = connection.getOutputStream();
      AttachmentCipherOutputStream out    = new AttachmentCipherOutputStream(key, stream);
      byte[]                       buffer = new byte[4096];
      int                   read, written = 0;

      while ((read = data.read(buffer)) != -1) {
        out.write(buffer, 0, read);
        written += read;

        if (listener != null) {
          listener.onAttachmentProgress(dataSize, written);
        }
      }

      data.close();
      out.flush();
      out.close();

      if (connection.getResponseCode() != 200) {
        throw new IOException("Bad response: " + connection.getResponseCode() + " " + connection.getResponseMessage());
      }

      return out.getAttachmentDigest();
    } finally {
      connection.disconnect();
    }
  }

  private String makeRequest(String urlFragment, String method, String body)
      throws NonSuccessfulResponseCodeException, PushNetworkException
  {
    Response response = getConnection(urlFragment, method, body);

    int    responseCode;
    String responseMessage;
    String responseBody;

    try {
      responseCode    = response.code();
      responseMessage = response.message();
      responseBody    = response.body().string();
    } catch (IOException ioe) {
      throw new PushNetworkException(ioe);
    }

    switch (responseCode) {
      case 413:
        throw new RateLimitException("Rate limit exceeded: " + responseCode);
      case 401:
      case 403:
        throw new AuthorizationFailedException("Authorization failed!");
      case 404:
        throw new NotFoundException("Not found");
      case 409:
        MismatchedDevices mismatchedDevices;

        try {
          mismatchedDevices = JsonUtil.fromJson(responseBody, MismatchedDevices.class);
        } catch (JsonProcessingException e) {
          Log.w(TAG, e);
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new MismatchedDevicesException(mismatchedDevices);
      case 410:
        StaleDevices staleDevices;

        try {
          staleDevices = JsonUtil.fromJson(responseBody, StaleDevices.class);
        } catch (JsonProcessingException e) {
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new StaleDevicesException(staleDevices);
      case 411:
        DeviceLimit deviceLimit;

        try {
          deviceLimit = JsonUtil.fromJson(responseBody, DeviceLimit.class);
        } catch (JsonProcessingException e) {
          throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " + responseMessage);
        } catch (IOException e) {
          throw new PushNetworkException(e);
        }

        throw new DeviceLimitExceededException(deviceLimit);
      case 417:
        throw new ExpectationFailedException();
    }

    if (responseCode != 200 && responseCode != 204) {
        throw new NonSuccessfulResponseCodeException("Bad response: " + responseCode + " " +
                                                     responseMessage);
    }

    return responseBody;
  }

  private Response getConnection(String urlFragment, String method, String body)
      throws PushNetworkException
  {
    try {
      SignalConnectionInformation connectionInformation = getRandom(signalConnectionInformation, random);
      String                      url                   = connectionInformation.getUrl();
      Optional<String>            hostHeader            = connectionInformation.getHostHeader();
      TrustManager[]              trustManagers         = connectionInformation.getTrustManagers();

      Log.w(TAG, "Push service URL: " + url);
      Log.w(TAG, "Opening URL: " + String.format("%s%s", url, urlFragment));

      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, trustManagers, null);

      OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()
          .sslSocketFactory(context.getSocketFactory(), (X509TrustManager)trustManagers[0])
          .connectTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS)
          .readTimeout(soTimeoutMillis, TimeUnit.MILLISECONDS);

      Request.Builder request = new Request.Builder();
      request.url(String.format("%s%s", url, urlFragment));

      if (body != null) {
        request.method(method, RequestBody.create(MediaType.parse("application/json"), body));
      } else {
        request.method(method, null);
      }

      if (credentialsProvider.getPassword() != null) {
        request.addHeader("Authorization", getAuthorizationHeader());
      }

      if (userAgent != null) {
        request.addHeader("X-Signal-Agent", userAgent);
      }

      if (connectionInformation.getConnectionSpec().isPresent()) {
        okHttpClientBuilder.connectionSpecs(Collections.singletonList(connectionInformation.getConnectionSpec().get()));
      } else {
        okHttpClientBuilder.connectionSpecs(Util.immutableList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS));
      }

      if (hostHeader.isPresent()) {
        okHttpClientBuilder.protocols(Collections.singletonList(Protocol.HTTP_1_1));
        request.addHeader("Host", hostHeader.get());
      }

      Call call = okHttpClientBuilder.build().newCall(request.build());

      synchronized (connections) {
        connections.add(call);
      }

      try {
        return call.execute();
      } finally {
        synchronized (connections) {
          connections.remove(call);
        }
      }
    } catch (IOException e) {
      throw new PushNetworkException(e);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new AssertionError(e);
    }
  }

  private String getAuthorizationHeader() {
    try {
      return "Basic " + Base64.encodeBytes((credentialsProvider.getUser() + ":" + credentialsProvider.getPassword()).getBytes("UTF-8"));
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }

  private SignalConnectionInformation getRandom(SignalConnectionInformation[] connections,
                                                SecureRandom random)
  {
    return connections[random.nextInt(connections.length)];
  }

  private static class GcmRegistrationId {

    @JsonProperty
    private String gcmRegistrationId;

    @JsonProperty
    private boolean webSocketChannel;

    public GcmRegistrationId() {}

    public GcmRegistrationId(String gcmRegistrationId, boolean webSocketChannel) {
      this.gcmRegistrationId = gcmRegistrationId;
      this.webSocketChannel  = webSocketChannel;
    }
  }

  private static class AttachmentDescriptor {
    @JsonProperty
    private long id;

    @JsonProperty
    private String location;

    public long getId() {
      return id;
    }

    public String getLocation() {
      return location;
    }
  }

  private static class SignalConnectionInformation {

    private final String                   url;
    private final Optional<String>         hostHeader;
    private final Optional<ConnectionSpec> connectionSpec;
    private final TrustManager[]           trustManagers;

    private SignalConnectionInformation(SignalServiceUrl signalServiceUrl) {
      this.url            = signalServiceUrl.getUrl();
      this.hostHeader     = signalServiceUrl.getHostHeader();
      this.connectionSpec = signalServiceUrl.getConnectionSpec();
      this.trustManagers  = BlacklistingTrustManager.createFor(signalServiceUrl.getTrustStore());
    }

    String getUrl() {
      return url;
    }

    Optional<String> getHostHeader() {
      return hostHeader;
    }

    TrustManager[] getTrustManagers() {
      return trustManagers;
    }

    Optional<ConnectionSpec> getConnectionSpec() {
      return connectionSpec;
    }
  }
}
