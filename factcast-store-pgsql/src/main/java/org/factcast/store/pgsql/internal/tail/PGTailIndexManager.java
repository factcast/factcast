package org.factcast.store.pgsql.internal.tail;

import org.factcast.core.subscription.observer.FastForwardTarget;

public interface PGTailIndexManager extends FastForwardTarget {
  void triggerTailCreation();
}
