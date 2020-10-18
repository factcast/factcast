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

import java.util.OptionalInt;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.subscription.FactTransformers;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.pgsql.internal.RequestedVersions;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.TimedOperation;

@RequiredArgsConstructor
public class FactTransformersImpl implements FactTransformers {

  @NonNull private final RequestedVersions requested;

  @NonNull private final FactTransformerService trans;

  @NonNull private final RegistryMetrics registryMetrics;

  @Override
  public @NonNull Fact transformIfNecessary(@NonNull Fact e) throws TransformationException {

    String ns = e.ns();
    String type = e.type();

    if (type == null
        || requested.dontCare(ns, type)
        || requested.exactVersion(ns, type, e.version())) {
      return e;
    } else {
      OptionalInt max = requested.get(ns, type).stream().mapToInt(v -> v).max();
      int targetVersion =
          max.orElseThrow(
              () -> new IllegalArgumentException("No requested Version !? This must not happen."));

      return registryMetrics.timed(
          TimedOperation.TRANSFORMATION,
          TransformationException.class,
          () -> trans.transformIfNecessary(e, targetVersion));
    }
  }
}
