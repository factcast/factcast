package org.factcast.itests.exception.handling;

import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.factus.HandlerFor;
import org.factcast.factus.projection.LocalManagedProjection;

public class LocalManagedUserNames extends LocalManagedProjection {
  @Getter private Throwable exception;

  @HandlerFor(ns = "users", type = "UserCreated", version = 2)
  void apply(Fact f) {}

  @Override
  public void onError(@NonNull Throwable exception) {
    this.exception = exception;

    super.onError(exception);
  }
}
