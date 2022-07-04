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

import java.util.*;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.transformation.FactTransformerService;
import org.factcast.core.subscription.transformation.FactTransformers;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.internal.RequestedVersions;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.jetbrains.annotations.Nullable;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FactTransformersImpl implements FactTransformers {

  @NonNull private final RequestedVersions requested;

  @NonNull private final FactTransformerService trans;

  @NonNull private final RegistryMetrics registryMetrics;

  @Nullable
  @Override
  public TransformationRequest prepareTransformation(@NonNull Fact e) {
    String ns = e.ns();
    String type = e.type();
    int version = e.version();

    if (type == null || requested.matches(ns, type, version)) {
      return null;
    } else {
      OptionalInt max = requested.get(ns, type).stream().mapToInt(v -> v).max();
      int targetVersion =
          max.orElseThrow(
              () -> new IllegalArgumentException("No requested Version !? This must not happen."));

      return new TransformationRequest(e, targetVersion);
    }
  }

  @Nullable
  @Override
  public Fact transform(@NonNull TransformationRequest req) {
    // TODO should move
    return registryMetrics.timed(
        RegistryMetrics.OP.TRANSFORMATION,
        TransformationException.class,
        () -> trans.transform(req));
  }
}
