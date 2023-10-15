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
package org.factcast.store.registry.validation.schema.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Tags;
import java.util.*;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.registry.validation.schema.SchemaConflictException;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Spy;

public abstract class AbstractSchemaStoreTest {

  @Spy protected RegistryMetrics registryMetrics = new NOPRegistryMetrics();

  private SchemaStore uut;

  @BeforeEach
  public void init() {
    uut = createUUT();
  }

  protected abstract SchemaStore createUUT();

  SchemaSource s = new SchemaSource();

  @Test
  void testEmptyContains() {
    s.id("http://testEmptyContains");
    s.hash("123");

    assertThat(uut.contains(s)).isFalse();
  }

  @Test
  void testEmptyGet() {
    Optional<String> actual = uut.get(SchemaKey.of("ns", "testEmptyGet", 5));
    assertThat(actual).isEmpty();
  }

  @Test
  void testGetAfterRegister() {

    s.id("http://testGetAfterRegister");
    s.hash("123");
    s.ns("ns");
    s.type("type");
    s.version(5);
    uut.register(s, "{}");

    Optional<String> actual = uut.get(s.toKey());
    assertThat(actual).isNotEmpty();
  }

  @Test
  void testContainsSensesConflict() {

    s.id("http://testContainsSensesConflict");
    s.hash("123");
    s.ns("ns");
    s.type("testContainsSensesConflict");
    s.version(5);
    uut.register(s, "{}");

    assertThrows(
        SchemaConflictException.class,
        () -> {
          SchemaSource conflicting = new SchemaSource();
          conflicting.id("http://testContainsSensesConflict");
          conflicting.hash("1234");
          uut.contains(conflicting);
        });

    verify(registryMetrics).count(eq(EVENT.SCHEMA_CONFLICT), any(Tags.class));
  }

  @Test
  public void testMultipleRegisterAttempts() {

    s.id("http://testMultipleRegisterAttempts");
    s.hash("123");
    s.ns("ns");
    s.type("testMultipleRegisterAttempts");
    s.version(5);

    uut.register(s, "{}");
    uut.register(s, "{\"a\": 1}");

    boolean actualContains = uut.contains(s);
    assertThat(actualContains).isTrue();

    Optional<String> actual = uut.get(s.toKey());
    assertThat(actual).isPresent().hasValue("{\"a\": 1}");
  }

  @Test
  public void testMatchingContains() {

    s.id("http://testMatchingContains");
    s.hash("123");
    s.ns("ns");
    s.type("testMatchingContains");
    s.version(5);
    uut.register(s, "{}");

    assertThat(uut.contains(s)).isTrue();
  }

  @Test
  void testGetAll() {

    s.id("http://getAll1");
    s.hash("123");
    s.ns("ns");
    s.type("type");
    s.version(5);
    uut.register(s, "{}");

    s = new SchemaSource();
    s.id("http://getAll2");
    s.hash("124");
    s.ns("ns");
    s.type("type");
    s.version(6);
    uut.register(s, "{}");

    assertThat(uut.getAllSchemaKeys())
        .containsExactlyInAnyOrder(SchemaKey.of("ns", "type", 5), SchemaKey.of("ns", "type", 6));
  }
}
