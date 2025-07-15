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
package org.factcast.test.mongo;

import eu.rekawek.toxiproxy.ToxiproxyClient;
import lombok.NonNull;
import org.factcast.test.toxi.AbstractToxiProxySupplier;
import org.testcontainers.containers.ToxiproxyContainer;

public class MongoProxy extends AbstractToxiProxySupplier {
  public MongoProxy(
      @NonNull ToxiproxyContainer.ContainerProxy proxy, @NonNull ToxiproxyClient client) {
    super(proxy, proxy.getName(), client);
  }
}
