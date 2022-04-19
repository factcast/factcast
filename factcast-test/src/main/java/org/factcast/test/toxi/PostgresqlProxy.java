package org.factcast.test.toxi;

import lombok.NonNull;
import org.testcontainers.containers.ToxiproxyContainer;

public class PostgresqlProxy extends AbstractToxiProxySupplier {
  public PostgresqlProxy(@NonNull ToxiproxyContainer.ContainerProxy proxy) {
    super(proxy);
  }
}
