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
package org.factcast.store.registry.transformation.store;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.*;
import java.util.concurrent.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.*;

@RequiredArgsConstructor
public class InMemTransformationStoreImpl extends AbstractTransformationStore {
  private final RegistryMetrics registryMetrics;

  private final Map<String, String> id2hashMap = new HashMap<>();

  private final Map<TransformationKey, List<Transformation>> transformationCache = new HashMap<>();

  @Override
  protected void doStore(@NonNull TransformationSource source, String transformation)
      throws TransformationConflictException {
    synchronized (mutex) {
      id2hashMap.put(source.id(), source.hash());
      List<Transformation> transformations = get(source.toKey());
      var t = SingleTransformation.of(source, transformation);
      // will replace the entry if (ns,type,from,to) are equal
      var index =
          ListUtils.indexOf(
              transformations,
              e ->
                  e.key().equals(t.key())
                      && e.fromVersion() == t.fromVersion()
                      && e.toVersion() == t.toVersion());

      if (index != -1) {
        transformations.set(index, t);
      } else {
        transformations.add(t);
      }
    }
  }

  @Override
  public boolean contains(@NonNull TransformationSource source)
      throws TransformationConflictException {
    synchronized (mutex) {
      String hash = id2hashMap.get(source.id());
      if (hash != null) {
        if (hash.equals(source.hash())) {
          return true;
        } else {
          registryMetrics.count(
              RegistryMetrics.EVENT.TRANSFORMATION_CONFLICT,
              Tags.of(Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, source.id())));

          throw new TransformationConflictException(
              "TransformationSource at " + source + " does not match the stored hash " + hash);
        }
      } else {
        return false;
      }
    }
  }

  private final Object mutex = new Object();

  @Override
  public List<Transformation> get(@NonNull TransformationKey key) {
    synchronized (mutex) {
      return transformationCache.computeIfAbsent(key, (k) -> new CopyOnWriteArrayList<>());
    }
  }

  @Override
  public void clearNearCache() {
    // There is no near cache
  }
}
