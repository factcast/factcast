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
package org.factcast.store.pgsql.registry.transformation.cache;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;
import org.factcast.core.Fact;
import org.factcast.store.pgsql.registry.NOPRegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetricsEvent;
import org.joda.time.DateTime;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.*;
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
    Optional<Fact> fact = uut.find(UUID.randomUUID(), 1, "1");

    assertThat(fact.isPresent()).isFalse();

    verify(registryMetrics).count(RegistryMetricsEvent.TRANSFORMATION_CACHE_MISS);
  }

  @Test
  void testFindAfterPut() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(fact, chainId);

    Optional<Fact> found = uut.find(fact.id(), fact.version(), chainId);

    assertThat(found.isPresent()).isTrue();
    assertEquals(fact, found.get());
    verify(registryMetrics).count(RegistryMetricsEvent.TRANSFORMATION_CACHE_HIT);
  }

  @Test
  void testCompact() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(fact, chainId);

    // clocks aren't synchronized so Im gonna add an hour here :)
    uut.compact(DateTime.now().plusHours(1));

    Optional<Fact> found = uut.find(fact.id(), fact.version(), chainId);

    assertThat(found.isPresent()).isFalse();
  }

  @Test
  void testNullContracts() {
    assertNpe(() -> uut.find(null, 1, "1"));
    assertNpe(() -> uut.find(UUID.randomUUID(), 1, null));
    assertNpe(() -> uut.put(null, ""));
    assertNpe(() -> uut.put(Fact.builder().buildWithoutPayload(), null));
  }

  private void assertNpe(Executable r) {
    assertThrows(NullPointerException.class, r);
  }

  @Test
  void testRespectsChainId() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(f, "foo");
    assertThat(uut.find(f.id(), 1, "xoo")).isEmpty();
  }

  @Test
  void testDoesNotFindUnknown() {
    uut.find(UUID.randomUUID(), 1, "foo");
  }

  @Test
  void testHappyPath() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(f, "foo");
    assertThat(uut.find(f.id(), 1, "foo")).contains(f);
  }

  @Test
  void testRespectsVersion() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(f, "foo");
    assertThat(uut.find(f.id(), 2, "foo")).isEmpty();
  }
}
