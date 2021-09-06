package org.factcast.store.registry.transformation;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;

public interface FactTransformer extends AutoCloseable {
  Fact transformIfNecessary(Fact e, int targetVersion) throws TransformationException;

  @Override
  default void close() {}
}
