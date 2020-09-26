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
package org.factcast.store.pgsql.registry.transformation.store;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.factcast.store.pgsql.registry.NOPRegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.MetricEvent;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.factcast.store.pgsql.registry.transformation.TransformationConflictException;
import org.factcast.store.pgsql.registry.transformation.TransformationKey;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.transformation.TransformationStoreListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.*;
import org.mockito.Spy;

public abstract class AbstractTransformationStoreTest {
  @Spy protected RegistryMetrics registryMetrics = new NOPRegistryMetrics();

  private TransformationStore uut;

  @BeforeEach
  public void init() {
    this.uut = createUUT();
  }

  protected abstract TransformationStore createUUT();

  final TransformationSource s = new TransformationSource();

  @Test
  void testEmptyContains() {
    s.id("http://testEmptyContains");
    s.hash("123");

    assertThat(uut.contains(s)).isFalse();
  }

  @Test
  void testEmptyGet() {
    List<Transformation> actual = uut.get(TransformationKey.of("ns", "testEmptyGet"));
    assertThat(actual).isEmpty();
  }

  @Test
  void testGetAfterRegister() {

    s.id("http://testGetAfterRegister");
    s.hash("123");
    s.ns("ns");
    s.type("type");
    s.from(1);
    s.to(2);
    uut.store(s, "");

    List<Transformation> actual = uut.get(s.toKey());
    assertThat(actual).isNotEmpty();
  }

  @Test
  void testContainsSensesConflict() {

    s.id("http://testContainsSensesConflict");
    s.hash("123");
    s.ns("ns");
    s.type("testContainsSensesConflict");
    s.from(1);
    s.to(2);
    uut.store(s, "");

    TransformationSource conflicting = new TransformationSource();
    conflicting.id("http://testContainsSensesConflict");
    conflicting.hash("1234");

    assertThrows(TransformationConflictException.class, () -> uut.contains(conflicting));

    verify(registryMetrics)
        .count(
            MetricEvent.TRANSFORMATION_CONFLICT,
            Tags.of(Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, conflicting.id())));
  }

  @Test
  void testNullContracts() {
    s.id("http://testContainsSensesConflict");
    s.hash("123");
    s.ns("ns");
    s.type("testContainsSensesConflict");
    s.from(1);
    s.to(2);

    assertNpe(() -> uut.contains(null));
    assertNpe(() -> uut.store(null, "{}"));
    assertNpe(() -> uut.get(null));
  }

  private void assertNpe(Executable r) {
    assertThrows(NullPointerException.class, r);
  }

  @Test
  public void testMatchingContains() {

    s.id("http://testMatchingContains");
    s.hash("123");
    s.ns("ns");
    s.type("testMatchingContains");
    s.from(1);
    s.to(2);
    uut.store(s, "{}");

    assertThat(uut.contains(s)).isTrue();
  }

  @Test
  public void testMultipleStoreAttempts() {

    s.id("http://testMatchingContains");
    s.hash("123");
    s.ns("ns");
    s.type("testMatchingContains");
    s.from(1);
    s.to(2);
    uut.store(s, "{}");
    uut.store(s, "{{}}");

    assertThat(uut.contains(s)).isTrue();
    assertEquals(1, uut.get(s.toKey()).size());
    assertThat(uut.get(s.toKey()).get(0).transformationCode()).hasValue("{{}}");
  }

  @Test
  public void testRegister() throws Exception {
    TransformationStoreListener l = mock(TransformationStoreListener.class);
    uut.register(l);

    TransformationSource source = new TransformationSource("xx", "hash", "ns", "type", 1, 2);
    uut.store(source, "");

    ((AbstractTransformationStore) uut)
        .executorService()
        .awaitTermination(200, TimeUnit.MILLISECONDS);

    TransformationKey key = source.toKey();
    verify(l).notifyFor(eq(key));
  }

  @Test
  public void testUnregister() throws Exception {
    TransformationStoreListener l = mock(TransformationStoreListener.class);
    uut.register(l);
    uut.unregister(l);

    TransformationSource source = new TransformationSource("xx", "hash", "ns", "type", 1, 2);
    uut.store(source, "");

    ((AbstractTransformationStore) uut)
        .executorService()
        .awaitTermination(200, TimeUnit.MILLISECONDS);

    verify(l, never()).notifyFor(any());
  }
}
