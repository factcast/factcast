package org.factcast.server.grpc;

import java.util.concurrent.atomic.AtomicReference;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;

@Slf4j
@RequiredArgsConstructor
public class OnCancelHandler implements Runnable {
  @NonNull private final String clientIdPrefix;
  @NonNull private final SubscriptionRequestTO req;
  @NonNull private final AtomicReference<Subscription> subRef;
  @NonNull private final GrpcObserverAdapter observer;

  @Override
  public void run() {
    log.debug(
        "{}got onCancel from stream, closing subscription {}", clientIdPrefix, req.debugInfo());
    try {
      subRef.get().close();
    } catch (Exception e) {
      log.debug("{}While closing connection after cancel", clientIdPrefix, e);
    }
    try {
      observer.shutdown();
    } catch (Exception e) {
      log.debug("{}While shutting down observer after cancel", clientIdPrefix, e);
    }
  }
}
