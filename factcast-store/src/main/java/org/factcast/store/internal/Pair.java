package org.factcast.store.internal;

import lombok.Value;

@Value(staticConstructor = "of")
public class Pair<L, R> {
  L left;
  R right;
}
