package org.factcast.test.toxi;

import lombok.NonNull;
import org.testcontainers.containers.ToxiproxyContainer;

public class FactCastProxy extends AbstractToxiProxySupplier {
  public FactCastProxy(@NonNull ToxiproxyContainer.ContainerProxy proxy) {
    super(proxy);
  }
}
