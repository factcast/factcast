package org.factcast.store.registry.transformation.chains;

import lombok.Value;
import org.factcast.store.registry.transformation.Transformation;

@Value(staticConstructor = "of")
class Edge {
  int fromVersion;

  int toVersion;

  Transformation transformation;

  public static Edge from(Transformation t) {
    return of(t.fromVersion(), t.toVersion(), t);
  }
}
