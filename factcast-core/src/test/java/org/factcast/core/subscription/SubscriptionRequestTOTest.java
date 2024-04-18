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
package org.factcast.core.subscription;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.*;

public class SubscriptionRequestTOTest {

  @Test
  void testDebugInfo() {
    SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch();
    SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
    assertEquals(r.debugInfo(), uut.debugInfo());
  }

  @Test
  void testDumpContainsDebugInfo() {
    SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch();
    SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
    assertTrue(uut.dump().contains(r.debugInfo()));
  }

  @Test
  void testToString() {
    SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch();
    String debugInfo = r.debugInfo();
    SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
    assertEquals(debugInfo, uut.toString());
  }

  @Test
  void testSpecsContainMarkSpec() {
    FactSpec s = FactSpec.ns("foo");
    SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
    SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
    assertEquals(1, uut.specs().size());
    assertEquals(s, uut.specs().get(0));
  }

  @Test
  void testHasAnyScriptFilters() {
    FactSpec s = FactSpec.ns("foo");
    SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
    SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
    assertFalse(uut.hasAnyScriptFilters());
    uut.addSpecs(
        Collections.singletonList(
            FactSpec.ns("buh").jsFilterScript("function (h,e){ return true }")));
    assertTrue(uut.hasAnyScriptFilters());
  }

  @Test
  void testAddSpecsNull() {
    Assertions.assertThrows(
        NullPointerException.class,
        () -> {
          FactSpec s = FactSpec.ns("foo");
          SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
          SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
          uut.addSpecs(null);
        });
  }

  @Test
  void testAddSpecsEmpty() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> {
          FactSpec s = FactSpec.ns("foo");
          SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
          SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
          uut.addSpecs(Collections.emptyList());
        });
  }

  @Test
  void testMaxDelay() {
    FactSpec s = FactSpec.ns("foo");
    SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
    SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
    assertEquals(SubscriptionRequestTO.DEFAULT_MAX_BATCH_DELAY_IN_MS, uut.maxBatchDelayInMs());
    uut.maxBatchDelayInMs(7);
    assertEquals(7, uut.maxBatchDelayInMs());
  }

  @Test
  void testAddSpecs() {
    FactSpec s = FactSpec.ns("foo");
    SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
    SubscriptionRequestTO uut = SubscriptionRequestTO.from(r);
    assertEquals(1, uut.specs().size());
    final String js = "function (h,e){ return true }";
    uut.addSpecs(Collections.singletonList(FactSpec.ns("buh").jsFilterScript(js)));
    assertEquals(2, uut.specs().size());
    assertEquals(js, uut.specs().get(1).jsFilterScript());
  }
}
