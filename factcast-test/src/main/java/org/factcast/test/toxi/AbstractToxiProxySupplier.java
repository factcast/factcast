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

import eu.rekawek.toxiproxy.Proxy;
import eu.rekawek.toxiproxy.ToxiproxyClient;
import java.util.function.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import org.factcast.test.FactCastIntegrationTestExecutionListener;
import org.factcast.test.FactCastIntegrationTestExecutionListener.ProxiedEndpoint;
import org.testcontainers.containers.ToxiproxyContainer;

@RequiredArgsConstructor
public abstract class AbstractToxiProxySupplier
    implements Supplier<ProxiedEndpoint> {
  @Delegate @NonNull private ProxiedEndpoint proxy;
  @NonNull private final ToxiproxyClient client;

  @Override
  public ProxiedEndpoint get() {
    return proxy;
  }

  @SneakyThrows
  public void reset() {
    client.reset();
  }

  @SneakyThrows
  public void disable() {
    client.getProxy(proxy.proxy().getName()).disable();
  }

  @SneakyThrows
  public void enable() {
    client.getProxy(proxy.proxy().getName()).enable();
  }

  @Override
  public String toString() {
    return this.getClass().getSimpleName()
        + "[listen="
        + proxy().getListen()
        + ",upstream="
        + proxy().getUpstream()
        + ",name="
        + proxy().getName()
        + "]";
  }
}
