package org.factcast.itests.exception.handling;

import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.factus.projection.WriterToken;

import java.time.Duration;
import java.util.UUID;

public class SubscribedUserNames implements SubscribedProjection {
  @Getter private Throwable exception;

  private UUID state = null;

  @HandlerFor(ns = "users", type = "UserCreated", version = 2)
  void apply(Fact f) {}

  @Override
  public void onError(@NonNull Throwable exception) {
    this.exception = exception;

    SubscribedProjection.super.onError(exception);
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
