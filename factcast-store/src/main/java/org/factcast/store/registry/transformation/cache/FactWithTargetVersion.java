package org.factcast.store.registry.transformation.cache;

import java.util.Comparator;

import org.factcast.core.Fact;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.transformation.chains.TransformationChain;

import lombok.NonNull;
import lombok.Value;

@Value
public class FactWithTargetVersion implements Comparable<FactWithTargetVersion> {
  int order;
  Fact fact;
  int targetVersion;
  TransformationKey transformationKey;
  TransformationChain transformationChain;

  @Override
  public int compareTo(@NonNull FactWithTargetVersion o) {
    return Comparator.comparing(FactWithTargetVersion::order).compare(this, o);
  }

  public FactWithTargetVersion replaceFactWith(Fact newFact) {
    return new FactWithTargetVersion(
        order, newFact, targetVersion, transformationKey, transformationChain);
  }
}
