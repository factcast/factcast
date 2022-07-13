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
package org.factcast.store.registry.transformation;

import com.google.common.collect.Sets;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.internal.RequestedVersions;
import org.factcast.store.registry.metrics.RegistryMetrics;

@RequiredArgsConstructor
public class FactTransformersImpl implements FactTransformers {

  @NonNull private final RequestedVersions requested;

  @NonNull private final FactTransformerService trans;

  @NonNull private final RegistryMetrics registryMetrics;

  @Override
  public @NonNull Fact transformIfNecessary(@NonNull Fact e) throws TransformationException {

    String ns = e.ns();
    String type = e.type();
    int version = e.version();

    if (type == null || requested.matches(ns, type, version)) {
      return e;
    } else {
      return registryMetrics.timed(
          RegistryMetrics.OP.TRANSFORMATION,
          TransformationException.class,
          // it is important to use a set here as we need the equality contract later...
          () -> trans.transformIfNecessary(e, Sets.newHashSet(requested.get(ns, type))));
    }
  }
}
