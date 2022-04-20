package org.factcast.test.redis;

import lombok.NonNull;
import org.factcast.test.toxi.AbstractToxiProxySupplier;
import org.testcontainers.containers.ToxiproxyContainer;

public class RedisProxy extends AbstractToxiProxySupplier {
  public RedisProxy(@NonNull ToxiproxyContainer.ContainerProxy proxy) {
    super(proxy);
  }
}
