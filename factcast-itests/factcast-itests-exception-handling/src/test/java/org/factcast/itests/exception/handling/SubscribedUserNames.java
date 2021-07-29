package org.factcast.itests.exception.handling;

import java.time.Duration;
import java.util.UUID;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.projection.WriterToken;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

@RequiredArgsConstructor
public class SubscribedUserNames implements SubscribedProjection {
  private final CountDownLatch catchupLatch;
  private final CountDownLatch errorLatch;

  @Getter private Throwable exception;

  private UUID state = null;

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
  public UUID state() {
    return state;
  }

  @Override
  public void state(@NonNull UUID state) {
    this.state = state;
  }

  @Override
  public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
    return () -> {};
  }
}
