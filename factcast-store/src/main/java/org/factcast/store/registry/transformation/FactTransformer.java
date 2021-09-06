package org.factcast.store.registry.transformation;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;

public interface FactTransformer {
  Fact transformIfNecessary(Fact e, int targetVersion) throws TransformationException;
}
