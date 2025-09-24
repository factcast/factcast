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
import java.util.function.Predicate;
import lombok.NonNull;
import org.factcast.core.*;
import org.factcast.core.spec.*;
import org.factcast.store.internal.PgFact;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.junit.jupiter.api.*;
import org.mockito.*;

/** see FactSpecMatcherScriptingTest for more tests including execution of scripts */
class JSFilterScriptMatcherTest {

  final JSEngineFactory ef = new GraalJSEngineFactory();

  @Test
  void testScriptMatch() {
    assertTrue(scriptMatch(FactSpec.ns("default"), PgFact.from(new TestFact())));
    assertFalse(
        scriptMatch(
            FactSpec.ns("default").filterScript(FilterScript.js("function (h,e){ return false }")),
            PgFact.from(new TestFact())));
    assertTrue(
        scriptMatch(
            FactSpec.ns("default")
                .filterScript(FilterScript.js("function (h,e){ return h.meta.x=='y' }")),
            PgFact.from(new TestFact().meta("x", "y"))));
  }

  private boolean scriptMatch(@NonNull FactSpec s, @NonNull PgFact f) {
    JSFilterScriptMatcher matches = JSFilterScriptMatcher.matches(s, ef);
    return matches == null || matches.scriptMatch(f);
  }

  @Test
  void testMatchesByScript() {
    String script = "function (h,p) { return p.test == 1 }";
    Predicate<PgFact> p =
        JSFilterScriptMatcher.matches(FactSpec.ns("1").filterScript(FilterScript.js(script)), ef);
    assertThat(p).isNotNull();
    assertTrue(p.test(PgFact.from(new TestFact().ns("1").jsonPayload("{\"test\":1}"))));
    assertFalse(p.test(PgFact.from(new TestFact().ns("1").jsonPayload("{\"test\":2}"))));
    assertFalse(p.test(PgFact.from(new TestFact().ns("1"))));
  }

  @Test
  void skipsBlank() {
    assertThat(
            JSFilterScriptMatcher.matches(FactSpec.ns("1").filterScript(FilterScript.js("  ")), ef))
        .isNull();
  }

  @Test
  void skipsEmpty() {
    assertThat(
            JSFilterScriptMatcher.matches(FactSpec.ns("1").filterScript(FilterScript.js("")), ef))
        .isNull();
  }

  @Test
  void doesNotSkipNonEmpty() {
    assertThat(
            JSFilterScriptMatcher.matches(
                FactSpec.ns("1").filterScript(FilterScript.js("  true ")), ef))
        .isNotNull();
  }
}
