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

import com.google.common.collect.Lists;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

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
  void testFindAll() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    Fact fact1 = Fact.builder().ns("ns").type("type").id(id1).version(1).build("{}");
    Fact fact2 = Fact.builder().ns("ns").type("type").id(id2).version(1).build("{}");
    Fact fact3 = Fact.builder().ns("ns").type("type").id(id3).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(TransformationCache.Key.of(fact1.id(), 1, chainId), fact1);
    uut.put(TransformationCache.Key.of(fact2.id(), 1, chainId), fact2);
    // but not fact3 !

    Collection<TransformationCache.Key> keys =
        Lists.newArrayList(
            TransformationCache.Key.of(fact1.id(), fact1.version(), chainId),
            TransformationCache.Key.of(fact2.id(), fact2.version(), chainId),
            TransformationCache.Key.of(fact3.id(), fact3.version(), chainId));
    var found = uut.findAll(keys);

    assertThat(found).hasSize(2).contains(fact1, fact2);
    verify(registryMetrics).increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT, 2);
    verify(registryMetrics).increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS, 1);
  }

  @Test
  void testFindAfterPut() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(TransformationCache.Key.of(fact.id(), 1, chainId), fact);

    Optional<Fact> found = uut.find(TransformationCache.Key.of(fact.id(), fact.version(), chainId));

    assertThat(found.isPresent()).isTrue();
    assertEquals(fact, found.get());
    verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT);
  }

  @Test
  void testCompact() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(TransformationCache.Key.of(fact.id(), 1, chainId), fact);

    // clocks aren't synchronized so Im gonna add an hour here :)
    uut.compact(ZonedDateTime.now().plusHours(1));

    Optional<Fact> found = uut.find(TransformationCache.Key.of(fact.id(), fact.version(), chainId));

    assertThat(found.isPresent()).isFalse();
  }

  @Test
  void testRespectsChainId() {
    Fact fact = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(TransformationCache.Key.of(fact.id(), 1, "foo"), fact);
    assertThat(uut.find(TransformationCache.Key.of(fact.id(), 1, "xoo"))).isEmpty();
  }

  @Test
  void testDoesNotFindUnknown() {
    uut.find(TransformationCache.Key.of(UUID.randomUUID(), 1, "foo"));
  }

  @Test
  void testHappyPath() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(TransformationCache.Key.of(f.id(), 1, "foo"), f);
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 1, "foo"))).contains(f);
  }

  @Test
  void testRespectsVersion() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(TransformationCache.Key.of(f.id(), 1, "foo"), f);
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 2, "foo"))).isEmpty();
  }

  @Test
  void testInvalidateTransformationForMatchingNamespaceAndType() {
    String matchingNs = "namespace";
    String matchingType = "type";
    Fact f1 = Fact.builder().ns(matchingNs).type(matchingType).version(1).build("{}");
    Fact f2 = Fact.builder().ns(matchingNs).type(matchingType).version(2).build("{}");
    uut.put(TransformationCache.Key.of(f1.id(), 1, "foo1"), f1);
    uut.put(TransformationCache.Key.of(f2.id(), 2, "foo2"), f2);

    uut.invalidateTransformationFor(matchingNs, matchingType);

    assertThat(uut.find(TransformationCache.Key.of(f1.id(), 1, "foo1"))).isEmpty();
    assertThat(uut.find(TransformationCache.Key.of(f2.id(), 2, "foo2"))).isEmpty();
  }
}
