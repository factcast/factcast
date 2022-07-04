package org.factcast.core.subscription.transformation;

import org.factcast.core.Fact;

import lombok.NonNull;
import lombok.Value;

@Value
public class TransformationRequest {
  @NonNull Fact toTransform;
  int targetVersion;
}
