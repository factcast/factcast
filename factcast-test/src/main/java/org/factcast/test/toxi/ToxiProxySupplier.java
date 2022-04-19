package org.factcast.test.toxi;

import java.util.function.Supplier;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

public interface ToxiProxySupplier extends Supplier<ContainerProxy> {}
