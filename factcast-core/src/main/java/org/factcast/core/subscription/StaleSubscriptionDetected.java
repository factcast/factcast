package org.factcast.core.subscription;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class StaleSubscriptionDetected extends RuntimeException {
  private static final long serialVersionUID = 5303452267477397256L;

  public StaleSubscriptionDetected(long last, long gracePeriod) {
    super(createMessage(last, gracePeriod));
  }

  private static String createMessage(long last, long gracePeriod) {
    if (last == 0L) {
      return "Even though expected due to requesting keepalive, the client did not receive any notification at all (waited for "
          + gracePeriod
          + "ms)";
    } else {
      return "Even though expected due to requesting keepalive, the client did not receive any notification for the last "
          + gracePeriod
          + "ms. (Last notification was received "
          + last
          + "ms ago)";
    }
  }
}
