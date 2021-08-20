package org.factcast.core.subscription.observer;

import java.util.UUID;
import javax.annotation.Nullable;
import lombok.Value;

public interface FastForwardTarget {
  static FastForwardTarget forTest() {
    return of(null, 0);
  }

  static FastForwardTarget of(UUID id, long ser) {
    return Impl.of(id, ser);
  }

  @Nullable
  UUID targetId();

  long targetSer();

  @Value(staticConstructor = "of")
  class Impl implements FastForwardTarget {
    UUID targetId;
    long targetSer;
  }
}
