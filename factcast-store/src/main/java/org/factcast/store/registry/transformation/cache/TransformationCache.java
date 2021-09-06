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
package org.factcast.store.registry.transformation.cache;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.joda.time.DateTime;

public interface TransformationCache {

  // maybe optimize by passing header and payload separately as
  // string/jsonnode?
  void put(Fact f, String transformationChainId);

  Optional<Fact> find(UUID eventId, int version, String transformationChainId);

  /**
   * Load given facts with target version, and enrich cached facts with the original {@link
   * FactWithTargetVersion}.
   *
   * @param factsWithTargetVersion
   * @return map with those given {@link FactWithTargetVersion} for which a cache entry was found as
   *     key and the cached fact in the desired version as value.
   */
  Map<FactWithTargetVersion, Fact> find(Collection<FactWithTargetVersion> factsWithTargetVersion);

  /** Put given freshly transformed facts into cache. */
  void put(Collection<FactWithTargetVersion> transformedFactsWithTargetVersion);

  void compact(DateTime thresholdDate);
}
