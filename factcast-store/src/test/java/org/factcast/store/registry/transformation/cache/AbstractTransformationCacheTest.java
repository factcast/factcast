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
import static org.mockito.Mockito.verify;

import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.store.internal.PgFact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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
    Optional<PgFact> fact = uut.find(TransformationCache.Key.of(UUID.randomUUID(), 1, "[1, 2, 3]"));

    assertThat(fact.isPresent()).isFalse();

    verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS);
  }

  @Test
  void testFindAll() {
    UUID id1 = UUID.randomUUID();
    UUID id2 = UUID.randomUUID();
    UUID id3 = UUID.randomUUID();
    PgFact fact1 = PgFact.from(Fact.builder().ns("ns").type("type").id(id1).version(1).build("{}"));
    PgFact fact2 = PgFact.from(Fact.builder().ns("ns").type("type").id(id2).version(1).build("{}"));
    PgFact fact3 = PgFact.from(Fact.builder().ns("ns").type("type").id(id3).version(1).build("{}"));

    uut.put(TransformationCache.Key.of(fact1.id(), 1, "[1, 2, 3]"), fact1);
    uut.put(TransformationCache.Key.of(fact2.id(), 1, "[1, 2, 3]"), fact2);
    // but not fact3 !

    Collection<TransformationCache.Key> keys =
        Lists.newArrayList(
            TransformationCache.Key.of(fact1.id(), fact1.version(), "[1, 2, 3]"),
            TransformationCache.Key.of(fact2.id(), fact2.version(), "[1, 2, 3]"),
            TransformationCache.Key.of(fact3.id(), fact3.version(), "[1, 2, 3]"));
    var found = uut.findAll(keys);

    assertThat(found).hasSize(2).contains(fact1, fact2);
    verify(registryMetrics).increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT, 2);
    verify(registryMetrics).increase(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS, 1);
  }

  @Test
  void testFindAfterPut() {
    PgFact fact =
        PgFact.from(
            Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}"));

    uut.put(TransformationCache.Key.of(fact.id(), 1, "[1, 2, 3]"), fact);

    Optional<PgFact> found =
        uut.find(TransformationCache.Key.of(fact.id(), fact.version(), "[1, 2, 3]"));

    assertThat(found.isPresent()).isTrue();
    assertEquals(fact, found.get());
    verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT);
  }

  @Test
  void testDoesNotFindUnknown() {
    uut.find(TransformationCache.Key.of(UUID.randomUUID(), 1, "[1, 2, 3]"));
  }

  @Test
  void testRespectsPath() {
    PgFact f = PgFact.from(Fact.builder().ns("name").type("type").version(1).build("{}"));

    uut.put(TransformationCache.Key.of(f.id(), 1, "[1, 2]"), f);
    // same fact + version but a different chain path is a distinct entry
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 1, "[1, 3]"))).isEmpty();
  }

  @Test
  void testHappyPath() {
    PgFact f = PgFact.from(Fact.builder().ns("name").type("type").version(1).build("{}"));

    uut.put(TransformationCache.Key.of(f.id(), 1, "[1, 2, 3]"), f);
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 1, "[1, 2, 3]"))).contains(f);
  }

  @Test
  void testRespectsVersion() {
    PgFact f = PgFact.from(Fact.builder().ns("name").type("type").version(1).build("{}"));

    uut.put(TransformationCache.Key.of(f.id(), 1, "[1, 2, 3]"), f);
    assertThat(uut.find(TransformationCache.Key.of(f.id(), 2, "[1, 2, 3]"))).isEmpty();
  }

  @Test
  void testInvalidateTransformationForMatchingNamespaceAndType() {
    String matchingNs = "namespace";
    String matchingType = "type";
    PgFact f1 =
        PgFact.from(Fact.builder().ns(matchingNs).type(matchingType).version(1).build("{}"));
    PgFact f2 =
        PgFact.from(Fact.builder().ns(matchingNs).type(matchingType).version(2).build("{}"));
    uut.put(TransformationCache.Key.of(f1.id(), 1, "[1, 2, 3]"), f1);
    uut.put(TransformationCache.Key.of(f2.id(), 2, "[1, 2, 3]"), f2);

    uut.invalidateTransformationFor(matchingNs, matchingType);

    assertThat(uut.find(TransformationCache.Key.of(f1.id(), 1, "[1, 2, 3]"))).isEmpty();
    assertThat(uut.find(TransformationCache.Key.of(f2.id(), 2, "[1, 2, 3]"))).isEmpty();
  }

  @Test
  void testInvalidateTransformationForMatchingFactId() {
    UUID matchingFactId = UUID.randomUUID();
    PgFact f1 = PgFact.from(Fact.builder().id(matchingFactId).version(1).build("{}"));
    PgFact f2 = PgFact.from(Fact.builder().id(UUID.randomUUID()).version(2).build("{}"));
    uut.put(TransformationCache.Key.of(f1.id(), 1, "[1, 2, 3]"), f1);
    uut.put(TransformationCache.Key.of(f1.id(), 2, "[1, 2, 3]"), f1);
    uut.put(TransformationCache.Key.of(f2.id(), 2, "[1, 2, 3]"), f2);

    uut.invalidateTransformationFor(matchingFactId);

    assertThat(uut.find(TransformationCache.Key.of(f1.id(), 1, "[1, 2, 3]"))).isEmpty();
    assertThat(uut.find(TransformationCache.Key.of(f1.id(), 2, "[1, 2, 3]"))).isEmpty();
    assertThat(uut.find(TransformationCache.Key.of(f2.id(), 2, "[1, 2, 3]"))).isNotEmpty();
  }
}
