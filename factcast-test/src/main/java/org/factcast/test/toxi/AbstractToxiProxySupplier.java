/*
 * Copyright © 2017-2022 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.test.toxi;

import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.util.function.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

@RequiredArgsConstructor
public abstract class AbstractToxiProxySupplier implements Supplier<ContainerProxy> {
  @Delegate @NonNull private ToxiproxyContainer.ContainerProxy proxy;
  @NonNull private final String name;
  @NonNull private final ToxiproxyClient client;

  @Override
  public ContainerProxy get() {
    return proxy;
  }

  @SneakyThrows
  public void reset() {
    client.reset();
  }

  @SneakyThrows
  public void disable() {
    client.getProxy(name).disable();
  }

  @SneakyThrows
  public void enable() {
    client.getProxy(name).enable();
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName()
        + "[ip="
        + getContainerIpAddress()
        + ",proxyPort="
        + getProxyPort()
        + ",origProxyPort="
        + getOriginalProxyPort()
        + ",name="
        + getName()
        + "]";
  }
}
