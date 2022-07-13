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

import java.util.function.*;

import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.script.engine.EngineFactory;
import org.factcast.script.engine.graaljs.GraalJSEngineCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactSpecMatcherScriptingTest {

  final EngineFactory ef = new GraalJSEngineCache();

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

  private boolean scriptMatch(FactSpec s, TestFact f) {
    return new FactSpecMatcher(s, ef).scriptMatch(f);
  }

  @Test
  void testMatchesByScript() {
    String script = "function (h,p) { return p.test == 1 }";
    Predicate<Fact> p = FactSpecMatcher.matches(FactSpec.ns("1").jsFilterScript(script), ef);
    assertTrue(p.test(new TestFact().ns("1").jsonPayload("{\"test\":1}")));
    assertFalse(p.test(new TestFact().ns("1").jsonPayload("{\"test\":2}")));
    assertFalse(p.test(new TestFact().ns("1")));
  }
}
