package org.factcast.store.internal.tail;

import org.factcast.core.subscription.observer.FastForwardTarget;

public interface PGTailIndexManager extends FastForwardTarget {
  void triggerTailCreation();
}
