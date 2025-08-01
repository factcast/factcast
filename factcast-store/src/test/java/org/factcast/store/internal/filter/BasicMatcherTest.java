/*
 * Copyright © 2017-2025 factcast.org
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

import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.Lists;
import java.util.*;
import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.store.internal.PgFact;
import org.junit.jupiter.api.*;

class BasicMatcherTest {

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
  private boolean test(FactSpec s, Fact f) {
    return new BasicMatcher(s).test(PgFact.from(f));
  }

  @Test
  void testMatchesByNS() {
    FactSpec fs = FactSpec.ns("1");
    assertTrue(test(fs, new TestFact().ns("1")));
    assertFalse(test(fs, new TestFact().ns("3")));
  }

  @Test
  void testMatchesByType() {
    FactSpec fs = FactSpec.ns("1").type("t1");
    assertTrue(test(fs, new TestFact().ns("1").type("t1")));
    assertFalse(test(fs, new TestFact().ns("1")));
  }

  @Test
  void testMatchesByVersion() {
    FactSpec fs = FactSpec.ns("1").version(1);
    assertTrue(test(fs, new TestFact().ns("1").version(1)));
    assertFalse(test(fs, new TestFact().ns("1").version(2)));
  }

  @Test
  void testMatchesByAggId() {
    FactSpec fs = FactSpec.ns("1").aggId(new UUID(0, 1));
    assertTrue(test(fs, new TestFact().ns("1").aggId(new UUID(0, 1))));
    assertFalse(test(fs, new TestFact().ns("1").aggId(new UUID(0, 2))));
  }

  @Test
  void testMatchesByMeta() {
    FactSpec fs = FactSpec.ns("1").meta("foo", "bar");
    assertTrue(test(fs, new TestFact().ns("1").meta("foo", "bar")));
    assertTrue(test(fs, new TestFact().ns("1").meta("poit", "zort").meta("foo", "bar")));
    assertFalse(test(fs, new TestFact().ns("1").meta("foo", "baz")));
    assertFalse(test(fs, new TestFact().ns("1")));
  }

  @Test
  void testMatchesByMetaAllMatch() {
    FactSpec fs = FactSpec.ns("1").meta("foo", "bar").meta("poit", "zort");
    assertTrue(
        test(
            fs,
            new TestFact().ns("1").meta("some", "other").meta("poit", "zort").meta("foo", "bar")));

    assertFalse(test(fs, new TestFact().ns("1").meta("foo", "bar")));
    assertFalse(test(fs, new TestFact().ns("1").meta("poit", "zort")));
    assertFalse(test(fs, new TestFact().ns("1")));
  }

  @Test
  void testMatchesNull() {
    TestHelper.expectNPE(() -> BasicMatcher.matches(null));
  }
}
