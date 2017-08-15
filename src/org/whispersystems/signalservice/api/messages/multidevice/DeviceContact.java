/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.messages.multidevice;

import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentStream;

public class DeviceContact {

  private final String                                  number;
  private final Optional<String>                        name;
  private final Optional<SignalServiceAttachmentStream> avatar;
  private final Optional<String>                        color;
  private final Optional<VerifiedMessage>               verified;

  public DeviceContact(String number, Optional<String> name,
                       Optional<SignalServiceAttachmentStream> avatar,
                       Optional<String> color,
                       Optional<VerifiedMessage> verified)
  {
    this.number   = number;
    this.name     = name;
    this.avatar   = avatar;
    this.color    = color;
    this.verified = verified;
  }

  public Optional<SignalServiceAttachmentStream> getAvatar() {
    return avatar;
  }

  public Optional<String> getName() {
    return name;
  }

  public String getNumber() {
    return number;
  }

  public Optional<String> getColor() {
    return color;
  }

  public Optional<VerifiedMessage> getVerified() {
    return verified;
  }
}
