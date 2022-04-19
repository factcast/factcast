package org.factcast.test.toxi;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

@RequiredArgsConstructor
abstract class AbstractToxiProxySupplier implements ToxiProxySupplier {
  @Delegate @NonNull private ToxiproxyContainer.ContainerProxy proxy;

  @Override
  public ContainerProxy get() {
    return proxy;
  }
}
