package org.factcast.test.toxi;

import java.util.function.Supplier;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

@RequiredArgsConstructor
public abstract class AbstractToxiProxySupplier implements Supplier<ContainerProxy> {
  @Delegate @NonNull private ToxiproxyContainer.ContainerProxy proxy;

  @Override
  public ContainerProxy get() {
    return proxy;
  }
}
