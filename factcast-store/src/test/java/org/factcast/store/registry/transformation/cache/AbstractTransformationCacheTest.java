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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.*;
import org.factcast.core.Fact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

public abstract class AbstractTransformationCacheTest {
  protected TransformationCache uut;

  @Spy protected RegistryMetrics registryMetrics = new NOPRegistryMetrics();

  @BeforeEach
  public void init() {
    uut = createUUT();
  }

  protected abstract TransformationCache createUUT();

  @Test
  void testEmptyFind() {
    Optional<Fact> fact = uut.find(TransformationCache.Key.of(UUID.randomUUID(), 1, "1"));

    assertThat(fact.isPresent()).isFalse();

    verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS);
  }

  @Test
  void testFindAfterPut() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(TransformationCache.Key.of(fact, chainId), fact);

    Optional<Fact> found = uut.find(TransformationCache.Key.of(fact.id(), fact.version(), chainId));

    assertThat(found.isPresent()).isTrue();
    assertEquals(fact, found.get());
    verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT);
  }

  @Test
  void testCompact() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(TransformationCache.Key.of(fact, chainId), fact);

    // clocks aren't synchronized so Im gonna add an hour here :)
    uut.compact(ZonedDateTime.now().plusHours(1));

    Optional<Fact> found = uut.find(TransformationCache.Key.of(fact.id(), fact.version(), chainId));

    assertThat(found.isPresent()).isFalse();
  }

  @Test
  void testRespectsChainId() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(TransformationCache.Key.of(f, "foo"), f);
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 1, "xoo"))).isEmpty();
  }

  @Test
  void testDoesNotFindUnknown() {
    uut.find(TransformationCache.Key.of(UUID.randomUUID(), 1, "foo"));
  }

  @Test
  void testHappyPath() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(TransformationCache.Key.of(f, "foo"), f);
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 1, "foo"))).contains(f);
  }

  @Test
  void testRespectsVersion() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(TransformationCache.Key.of(f, "foo"), f);
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 2, "foo"))).isEmpty();
  }
}
