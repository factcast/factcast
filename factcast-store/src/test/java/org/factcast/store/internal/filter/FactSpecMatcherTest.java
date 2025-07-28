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

import com.google.common.collect.Lists;
import java.util.*;
import java.util.function.Predicate;
import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

/** see FactSpecMatcherScriptingTest for more tests including execution of scripts */
class FactSpecMatcherTest {

  final JSEngineFactory ef = new GraalJSEngineFactory();

  @Test
  void testScriptMatch() {
    assertTrue(scriptMatch(FactSpec.ns("default"), new TestFact()));
    assertFalse(
        scriptMatch(
            FactSpec.ns("default").filterScript(FilterScript.js("function (h,e){ return false }")),
            new TestFact()));
    assertTrue(
        scriptMatch(
            FactSpec.ns("default")
                .filterScript(FilterScript.js("function (h,e){ return h.meta.x=='y' }")),
            new TestFact().meta("x", "y")));
  }

  private boolean scriptMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s, ef).scriptMatch(f);
  }

  @Test
  void testMatchesByScript() {
    String script = "function (h,p) { return p.test == 1 }";
    Predicate<Fact> p =
        FactSpecMatcher.matches(FactSpec.ns("1").filterScript(FilterScript.js(script)), ef);
    assertTrue(p.test(new TestFact().ns("1").jsonPayload("{\"test\":1}")));
    assertFalse(p.test(new TestFact().ns("1").jsonPayload("{\"test\":2}")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMetaMatch() {
    assertTrue(test(FactSpec.ns("default").meta("foo", "bar"), new TestFact().meta("foo", "bar")));
    assertTrue(
        test(
            FactSpec.ns("default").meta("foo", "bar"),
            new TestFact().meta("x", "y").meta("foo", "bar")));
    assertTrue(test(FactSpec.ns("default"), new TestFact().meta("x", "y").meta("foo", "bar")));
    assertFalse(test(FactSpec.ns("default").meta("foo", "bar"), new TestFact().meta("foo", "baz")));
    assertFalse(test(FactSpec.ns("default").meta("foo", "bar"), new TestFact()));
  }

  @Test
  void testMetaMatchForMultipleValues() {
    TestFact f = new TestFact().meta("foo", "bar").meta("baz", Lists.newArrayList("1", "2", "3"));
    assertTrue(test(FactSpec.ns("default").meta("foo", "bar"), f));
    assertTrue(test(FactSpec.ns("default").meta("baz", "1"), f));
    assertTrue(test(FactSpec.ns("default").meta("baz", "2"), f));
    assertTrue(test(FactSpec.ns("default").meta("baz", "3"), f));
  }

  @Test
  void testMetaExistsMatch() {
    assertFalse(test(FactSpec.ns("default").metaExists("foo"), new TestFact()));

    assertTrue(
        test(
            FactSpec.ns("default").metaDoesNotExist("narf"),
            new TestFact().meta("x", "y").meta("foo", "bar")));

    assertTrue(
        test(
            FactSpec.ns("default").metaExists("narf"),
            new TestFact().meta("x", "y").meta("narf", "bar")));
  }

  @Test
  void testNsMatch() {
    assertTrue(test(FactSpec.ns("default"), new TestFact().ns("default")));
    assertFalse(test(FactSpec.ns("default"), new TestFact().ns("xxx")));
  }

  @Test
  void testTypeMatch() {
    assertTrue(test(FactSpec.ns("default").type("a"), new TestFact().type("a")));
    assertTrue(test(FactSpec.ns("default"), new TestFact().type("a")));
    assertFalse(test(FactSpec.ns("default").type("a"), new TestFact().type("x")));
    assertFalse(test(FactSpec.ns("default").type("a"), new TestFact()));
  }

  @Test
  void testVersionMatch() {
    assertTrue(test(FactSpec.ns("default").version(1), new TestFact().version(1)));
    assertTrue(test(FactSpec.ns("default"), new TestFact().version(3)));
    assertFalse(test(FactSpec.ns("default").version(2), new TestFact()));
  }

  @Test
  void testAggIdMatch() {
    UUID u1 = UUID.randomUUID();
    UUID u2 = UUID.randomUUID();
    assertTrue(test(FactSpec.ns("default").aggId(u1), new TestFact().aggId(u1)));
    assertTrue(test(FactSpec.ns("default"), new TestFact().aggId(u1)));
    assertTrue(test(FactSpec.ns("default").aggId(u1), new TestFact().aggId(u1, u2)));
    assertTrue(test(FactSpec.ns("default").aggId(u1, u2), new TestFact().aggId(u2, u1)));
    assertFalse(test(FactSpec.ns("default").aggId(u1), new TestFact().aggId(u2)));
    assertFalse(test(FactSpec.ns("default").aggId(u1), new TestFact()));
    assertFalse(test(FactSpec.ns("default").aggId(u1, u2), new TestFact().aggId(u1)));
  }

  // ---------------------------
  private boolean test(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s, ef).test(f);
  }

  @Test
  void testMatchesAnyOf() {
    Predicate<Fact> p =
        FactSpecMatcher.matchesAnyOf(Arrays.asList(FactSpec.ns("1"), FactSpec.ns("2")), ef);
    assertTrue(p.test(new TestFact().ns("1")));
    assertTrue(p.test(new TestFact().ns("2")));
    assertFalse(p.test(new TestFact().ns("3")));
  }

  @Test
  void testMatchesByNS() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1"), ef);
    assertTrue(p.test(new TestFact().ns("1")));
    assertFalse(p.test(new TestFact().ns("3")));
  }

  @Test
  void testMatchesByType() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").type("t1"), ef);
    assertTrue(p.test(new TestFact().ns("1").type("t1")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMatchesByVersion() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").version(1), ef);
    assertTrue(p.test(new TestFact().ns("1").version(1)));
    assertFalse(p.test(new TestFact().ns("1").version(2)));
  }

  @Test
  void testMatchesByAggId() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").aggId(new UUID(0, 1)), ef);
    assertTrue(p.test(new TestFact().ns("1").aggId(new UUID(0, 1))));
    assertFalse(p.test(new TestFact().ns("1").aggId(new UUID(0, 2))));
  }

  @Test
  void testMatchesByMeta() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").meta("foo", "bar"), ef);
    assertTrue(p.test(new TestFact().ns("1").meta("foo", "bar")));
    assertTrue(p.test(new TestFact().ns("1").meta("poit", "zort").meta("foo", "bar")));
    assertFalse(p.test(new TestFact().ns("1").meta("foo", "baz")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMatchesByMetaAllMatch() {
    Predicate<Fact> p =
        FactSpecMatcher.matches(FactSpec.ns("1").meta("foo", "bar").meta("poit", "zort"), ef);
    assertTrue(
        p.test(
            new TestFact().ns("1").meta("some", "other").meta("poit", "zort").meta("foo", "bar")));

    assertFalse(p.test(new TestFact().ns("1").meta("foo", "bar")));
    assertFalse(p.test(new TestFact().ns("1").meta("poit", "zort")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMatchesNull() {
    TestHelper.expectNPE(() -> FactSpecMatcher.matches(null, ef));
  }

  @Nested
  class FieldName {
    @Test
    void returnsSimpleName() {
      String probe = "a";
      assertThat(FactSpecMatcher.fieldName(probe)).isSameAs(probe);
    }

    @Test
    void returnsLastPart() {
      String probe = "a.b.c.d";
      assertThat(FactSpecMatcher.fieldName(probe)).isEqualTo("d");
    }
  }

  @Nested
  class AggregateIdProperty {

    @Test
    void notContained() {
      Fact f = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).build("{}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo", UUID.randomUUID());

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isFalse();
    }

    @Test
    void notContainedNested() {
      Fact f = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).build("{}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.baz", UUID.randomUUID());

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isFalse();
    }

    @Test
    void notContainedArray() {

      Fact f =
          Fact.builder()
              .ns("ns")
              .type("type")
              .id(UUID.randomUUID())
              .build("{\"foo\":[\"" + UUID.randomUUID() + ",\"" + UUID.randomUUID() + "\"]}");
      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo", UUID.randomUUID());
      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isFalse();
    }

    @Test
    void notContainedNonUnique() {
      UUID id = UUID.randomUUID();
      Fact f =
          Fact.builder()
              .ns("ns")
              .type("type")
              .id(UUID.randomUUID())
              .build("{\"baz\":\"" + id + "\"}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.baz", id);

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isFalse();
    }

    @Test
    void notContainedNonUniqueNested() {
      UUID id = UUID.randomUUID();
      Fact f =
          Fact.builder()
              .ns("ns")
              .type("type")
              .id(UUID.randomUUID())
              .build("{\"foo\":{\"baz\":\"" + id + "\"}}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.baz", id);

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isFalse();
    }

    @Test
    void contained() {
      UUID id = UUID.randomUUID();
      Fact f =
          Fact.builder()
              .ns("ns")
              .type("type")
              .aggId(id)
              .id(UUID.randomUUID())
              .build("{\"id\":\"" + id + "\"}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("id", id);

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isTrue();
    }

    @Test
    void containedNested() {
      UUID id = UUID.randomUUID();
      Fact f =
          Fact.builder()
              .ns("ns")
              .type("type")
              .aggId(id)
              .id(UUID.randomUUID())
              .build("{\"foo\":{\"id\":\"" + id + "\"}}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.id", id);

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isTrue();
    }

    @Test
    void containedArrayMiss() {
      UUID id = UUID.randomUUID();
      Fact f =
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
                      + "\"]}}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.id", id);

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isFalse();
    }

    @Test
    void containedNested2() {
      UUID id = UUID.randomUUID();
      Fact f =
          Fact.builder()
              .ns("ns")
              .type("type")
              .aggId(id)
              .id(UUID.randomUUID())
              .build("{\"foo\":{\"bar\":{\"id\":\"" + id + "\"}}}");

      FactSpec spec = new FactSpec("*");
      spec.aggIdProperty("foo.bar.id", id);

      assertThat(new FactSpecMatcher(spec, Mockito.mock(JSEngineFactory.class)).test(f)).isTrue();
    }
  }
}
