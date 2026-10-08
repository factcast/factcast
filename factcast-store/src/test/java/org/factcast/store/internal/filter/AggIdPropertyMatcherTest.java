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
package org.factcast.store.internal.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.store.internal.PgFact;
import org.junit.jupiter.api.*;
import org.mockito.*;

/** see FactSpecMatcherScriptingTest for more tests including execution of scripts */
class AggIdPropertyMatcherTest {

  @Nested
  class FieldName {
    @Test
    void returnsSimpleName() {
      String probe = "a";
      assertThat(AggIdPropertyMatcher.fieldName(probe)).isSameAs(probe);
    }

    @Test
    void returnsLastPart() {
      String probe = "a.b.c.d";
      assertThat(AggIdPropertyMatcher.fieldName(probe)).isEqualTo("d");
    }
  }

  @Nested
  class AggregateIdProperty {

    @Test
    void notContained() {
      PgFact f =
          PgFact.from(Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).build("{}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo", UUID.randomUUID());

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isFalse();
    }

    @Test
    void notContainedNested() {
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .id(UUID.randomUUID())
                  .build("{\"foo\":\"bar\"}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.baz", UUID.randomUUID());

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isFalse();
    }

    @Test
    void notContainedArray() {

      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .id(UUID.randomUUID())
                  .build(
                      "{\"foo\":[\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID() + "\"]}"));
      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo", UUID.randomUUID());
      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isFalse();
    }

    @Test
    void notContainedNonUnique() {
      UUID id = UUID.randomUUID();
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .id(UUID.randomUUID())
                  .build("{\"baz\":\"" + id + "\"}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.baz", id);

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isFalse();
    }

    @Test
    void notContainedNonUniqueNested() {
      UUID id = UUID.randomUUID();
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .id(UUID.randomUUID())
                  .build("{\"foo\":{\"baz\":\"" + id + "\"}}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.baz", id);

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isFalse();
    }

    @Test
    void contained() {
      UUID id = UUID.randomUUID();
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .aggId(id)
                  .id(UUID.randomUUID())
                  .build("{\"id\":\"" + id + "\"}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("id", id);

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isTrue();
    }

    @Test
    void containedNested() {
      UUID id = UUID.randomUUID();
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .aggId(id)
                  .id(UUID.randomUUID())
                  .build("{\"foo\":{\"id\":\"" + id + "\"}}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.id", id);

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isTrue();
    }

    @Test
    void containedArrayMiss() {
      UUID id = UUID.randomUUID();
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .aggId(id)
                  .id(UUID.randomUUID())
                  .build(
                      "{\"foo\":{\"id\":[\""
                          + UUID.randomUUID()
                          + "\",\""
                          + UUID.randomUUID()
                          + "\"]}}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.id", id);

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isFalse();
    }

    @Test
    void containedNested2() {
      UUID id = UUID.randomUUID();
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .aggId(id)
                  .id(UUID.randomUUID())
                  .build("{\"foo\":{\"bar\":{\"id\":\"" + id + "\"}}}"));

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.id", id);

      assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isTrue();
    }

    @Test
    void aggIdPropertiesMatch() {
      @NonNull UUID id = UUID.fromString("cf29f467-e22c-4192-9590-39a5a2958cf1");
      PgFact f =
          PgFact.from(
              Fact.builder()
                  .aggId(id)
                  .type("x")
                  .ns("y")
                  .version(1)
                  .build(
                      "{\"aggregateId\":\"cf29f467-e22c-4192-9590-39a5a2958cf1\",\"userName\":\"John\"}"));
      @NonNull
      FactSpec spec =
          FactSpec.ns("y").type("x").version(1).aggId(id).aggIdProperty("aggregateId", id);
      Assertions.assertThat(AggIdPropertyMatcher.matches(spec).test(f)).isTrue();
    }
  }
}
