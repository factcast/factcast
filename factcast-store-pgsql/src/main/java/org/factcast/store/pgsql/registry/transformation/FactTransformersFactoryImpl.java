/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.pgsql.registry.transformation;

import lombok.RequiredArgsConstructor;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.FactTransformersFactory;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.RequestedVersions;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;

@RequiredArgsConstructor
public class FactTransformersFactoryImpl implements FactTransformersFactory {

  private final FactTransformerService trans;

  private final RegistryMetrics registryMetrics;

  @Override
  public FactTransformers createFor(SubscriptionRequestTO sr) {

    RequestedVersions rv = new RequestedVersions();

    sr.specs()
        .forEach(
            s -> {
              if (s.type() != null) {
                rv.add(s.ns(), s.type(), s.version());
              }
            });

    return new FactTransformersImpl(rv, trans, registryMetrics);
  }
}
