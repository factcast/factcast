package org.factcast.client.grpc;

import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.RequestedVersions;

public class NullFactTransformer extends FactTransformers {
  public NullFactTransformer() {
    super(new RequestedVersions());
  }
}
