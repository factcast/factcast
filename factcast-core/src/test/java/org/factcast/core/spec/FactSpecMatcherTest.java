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
package org.factcast.core.spec;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.Predicate;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.TestHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FactSpecMatcherTest {

  @Test
  void testMetaMatch() {
    assertTrue(
        metaMatch(FactSpec.ns("default").meta("foo", "bar"), new TestFact().meta("foo", "bar")));
    assertTrue(
        metaMatch(
            FactSpec.ns("default").meta("foo", "bar"),
            new TestFact().meta("x", "y").meta("foo", "bar")));
    assertTrue(metaMatch(FactSpec.ns("default"), new TestFact().meta("x", "y").meta("foo", "bar")));
    assertFalse(
        metaMatch(FactSpec.ns("default").meta("foo", "bar"), new TestFact().meta("foo", "baz")));
    assertFalse(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new TestFact()));
  }

  @Test
  void testNsMatch() {
    assertTrue(nsMatch(FactSpec.ns("default"), new TestFact().ns("default")));
    assertFalse(nsMatch(FactSpec.ns("default"), new TestFact().ns("xxx")));
  }

  @Test
  void testTypeMatch() {
    assertTrue(typeMatch(FactSpec.ns("default").type("a"), new TestFact().type("a")));
    assertTrue(typeMatch(FactSpec.ns("default"), new TestFact().type("a")));
    assertFalse(typeMatch(FactSpec.ns("default").type("a"), new TestFact().type("x")));
    assertFalse(typeMatch(FactSpec.ns("default").type("a"), new TestFact()));
  }

  @Test
  void testVersionMatch() {
    assertTrue(versionMatch(FactSpec.ns("default").version(1), new TestFact().version(1)));
    assertTrue(versionMatch(FactSpec.ns("default"), new TestFact().version(3)));
    assertFalse(versionMatch(FactSpec.ns("default").version(2), new TestFact()));
  }

  @Test
  void testAggIdMatch() {
    UUID u1 = UUID.randomUUID();
    UUID u2 = UUID.randomUUID();
    assertTrue(aggIdMatch(FactSpec.ns("default").aggId(u1), new TestFact().aggId(u1)));
    assertTrue(aggIdMatch(FactSpec.ns("default"), new TestFact().aggId(u1)));
    assertFalse(aggIdMatch(FactSpec.ns("default").aggId(u1), new TestFact().aggId(u2)));
    assertFalse(aggIdMatch(FactSpec.ns("default").aggId(u1), new TestFact()));
  }

  @Test
  void testScriptMatch() {
    assertTrue(scriptMatch(FactSpec.ns("default"), new TestFact()));
    assertFalse(
        scriptMatch(
            FactSpec.ns("default").jsFilterScript("function (h,e){ return false }"),
            new TestFact()));
    assertTrue(
        scriptMatch(
            FactSpec.ns("default").jsFilterScript("function (h,e){ return h.meta.x=='y' }"),
            new TestFact().meta("x", "y")));
  }

  // ---------------------------
  private boolean nsMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s).nsMatch(f);
  }

  private boolean typeMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s).typeMatch(f);
  }

  private boolean versionMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s).versionMatch(f);
  }

  private boolean aggIdMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s).aggIdMatch(f);
  }

  private boolean scriptMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s).scriptMatch(f);
  }

  private boolean metaMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s).metaMatch(f);
  }

  @Test
  void testMatchesAnyOfNull() {
    Assertions.assertThrows(NullPointerException.class, () -> FactSpecMatcher.matchesAnyOf(null));
  }

  @Test
  void testMatchesAnyOf() {
    Predicate<Fact> p =
        FactSpecMatcher.matchesAnyOf(Arrays.asList(FactSpec.ns("1"), FactSpec.ns("2")));
    assertTrue(p.test(new TestFact().ns("1")));
    assertTrue(p.test(new TestFact().ns("2")));
    assertFalse(p.test(new TestFact().ns("3")));
  }

  @Test
  void testMatchesByNS() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1"));
    assertTrue(p.test(new TestFact().ns("1")));
    assertFalse(p.test(new TestFact().ns("3")));
  }

  @Test
  void testMatchesByType() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").type("t1"));
    assertTrue(p.test(new TestFact().ns("1").type("t1")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMatchesByVersion() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").version(1));
    assertTrue(p.test(new TestFact().ns("1").version(1)));
    assertFalse(p.test(new TestFact().ns("1").version(2)));
  }

  @Test
  void testMatchesByAggId() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").aggId(new UUID(0, 1)));
    assertTrue(p.test(new TestFact().ns("1").aggId(new UUID(0, 1))));
    assertFalse(p.test(new TestFact().ns("1").aggId(new UUID(0, 2))));
  }

  @Test
  void testMatchesByMeta() {
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").meta("foo", "bar"));
    assertTrue(p.test(new TestFact().ns("1").meta("foo", "bar")));
    assertTrue(p.test(new TestFact().ns("1").meta("poit", "zort").meta("foo", "bar")));
    assertFalse(p.test(new TestFact().ns("1").meta("foo", "baz")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMatchesByMetaAllMatch() {
    Predicate<Fact> p =
        FactSpecMatcher.matches(FactSpec.ns("1").meta("foo", "bar").meta("poit", "zort"));
    assertTrue(
        p.test(
            new TestFact().ns("1").meta("some", "other").meta("poit", "zort").meta("foo", "bar")));

    assertFalse(p.test(new TestFact().ns("1").meta("foo", "bar")));
    assertFalse(p.test(new TestFact().ns("1").meta("poit", "zort")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMatchesByScript() {
    String script = "function (h,p) { return p.test == 1 }";
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").jsFilterScript(script));
    assertTrue(p.test(new TestFact().ns("1").jsonPayload("{\"test\":1}")));
    assertFalse(p.test(new TestFact().ns("1").jsonPayload("{\"test\":2}")));
    assertFalse(p.test(new TestFact().ns("1")));
  }

  @Test
  void testMatchesNull() {
    TestHelper.expectNPE(() -> FactSpecMatcher.matches(null));
  }
}
