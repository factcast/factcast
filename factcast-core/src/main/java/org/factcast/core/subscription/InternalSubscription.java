package org.factcast.core.subscription;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.UUID;
import lombok.NonNull;
import org.factcast.core.Fact;

public interface InternalSubscription extends Subscription {
  void close();

  @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
  void notifyCatchup();

  void notifyFastForward(@NonNull UUID factId);

  void notifyFactStreamInfo(@NonNull FactStreamInfo info);

  @SuppressFBWarnings("NP_NONNULL_PARAM_VIOLATION")
  void notifyComplete();

  void notifyError(Throwable e);

  void notifyElement(@NonNull Fact e) throws TransformationException;

  SubscriptionImpl onClose(Runnable e);

  java.util.concurrent.atomic.AtomicLong factsNotTransformed();

  java.util.concurrent.atomic.AtomicLong factsTransformed();
}
