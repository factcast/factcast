package org.factcast.itests.exception.handling;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.projection.WriterToken;

@RequiredArgsConstructor
public class SubscribedUserNames implements SubscribedProjection {
  private final CountDownLatch catchupLatch;
  private final CountDownLatch errorLatch;

  @Getter private Throwable exception;

  private UUID factStreamPosition = null;

  @HandlerFor(ns = "users", type = "UserCreated", version = 2)
  void apply(Fact f) {}

  @Override
  public void onError(@NonNull Throwable exception) {
    this.exception = exception;
    errorLatch.countDown();
    SubscribedProjection.super.onError(exception);
  }

  @Override
  public void onCatchup() {
    catchupLatch.countDown();
    SubscribedProjection.super.onCatchup();
  }

  @Override
  public UUID factStreamPosition() {
    return factStreamPosition;
  }

  @Override
  public void factStreamPosition(@NonNull UUID pos) {
    factStreamPosition = pos;
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return () -> {};
  }
}
