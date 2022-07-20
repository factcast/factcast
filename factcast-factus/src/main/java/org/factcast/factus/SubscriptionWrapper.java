package org.factcast.factus;

import java.io.Closeable;

import org.factcast.core.subscription.Subscription;

import lombok.*;
import lombok.experimental.Delegate;

@RequiredArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class SubscriptionWrapper implements Subscription {
  @Delegate(excludes = Closeable.class)
  @NonNull
  private final Subscription delegate;

  @With @NonNull private Runnable onCloseHandler = () -> {};

  @Override
  public void close() throws Exception {
    try {
      onCloseHandler.run();
    } finally {
      delegate.close();
    }
  }
}
