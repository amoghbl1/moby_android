package org.thoughtcrime.securesms.sms;

import org.thoughtcrime.securesms.recipients.Recipients;

public class OutgoingHerdMessage extends OutgoingTextMessage {

  public OutgoingHerdMessage(Recipients recipients, String body) {
    super(recipients, body, 0, -1);
  }

  private OutgoingHerdMessage(OutgoingHerdMessage base, String body) {
    super(base, body);
  }


  @Override
  public boolean isSecureMessage() {
    return true;
  }

  @Override
  public OutgoingTextMessage withBody(String body) {
    return new OutgoingHerdMessage(this, body);
  }
}
