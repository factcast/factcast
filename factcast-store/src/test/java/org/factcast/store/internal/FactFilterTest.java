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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.core.TestFact;
import org.factcast.core.spec.*;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.internal.filter.*;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactFilterTest {

  @Nested
  class WhenTesting {
    GraalJSEngineFactory ef = new GraalJSEngineFactory();

    @Test
    void skipsIfNoFilterScriptAndNoAggregateIdPropertyInvolved() {
      @NonNull FactSpec spec = FactSpec.ns("x").type("y").version(2);
      @NonNull SubscriptionRequest req = SubscriptionRequest.catchup(spec).fromScratch();
      FactFilter uut = new FactFilter(req, ef);
      assertThat(uut.canBeSkipped()).isTrue();
    }

    @Test
    void testOnFilterScriptTrue() {
      @NonNull
      FactSpec spec =
          FactSpec.ns("x")
              .type("y")
              .version(2)
              .filterScript(FilterScript.js("function(a,b){return true;};"));
      @NonNull SubscriptionRequest req = SubscriptionRequest.catchup(spec).fromScratch();
      FactFilter uut = new FactFilter(req, ef);
      assertThat(uut.canBeSkipped()).isFalse();

      Assertions.assertThat(uut.test(PgFact.from(new TestFact().ns("x").type("y").version(2))))
          .isTrue();
    }

    @Test
    void testOnFilterScriptFalse() {
      @NonNull
      FactSpec spec =
          FactSpec.ns("x")
              .type("y")
              .version(2)
              .filterScript(FilterScript.js("function(a,b){return false;};"));
      @NonNull SubscriptionRequest req = SubscriptionRequest.catchup(spec).fromScratch();
      FactFilter uut = new FactFilter(req, ef);
      assertThat(uut.canBeSkipped()).isFalse();

      Assertions.assertThat(uut.test(PgFact.from(new TestFact().ns("x").type("y").version(2))))
          .isFalse();
    }

    @Test
    void testOnFilterScriptBasicMismatch() {
      @NonNull
      FactSpec spec =
          FactSpec.ns("x")
              .type("y")
              .version(2)
              .filterScript(FilterScript.js("function(a,b){return true;};"));
      @NonNull SubscriptionRequest req = SubscriptionRequest.catchup(spec).fromScratch();
      FactFilter uut = new FactFilter(req, ef);
      assertThat(uut.canBeSkipped()).isFalse();

      Assertions.assertThat(uut.test(PgFact.from(new TestFact().ns("a").type("b").version(2))))
          .isFalse();
    }

    @Test
    void testOnAggIdProperty() {
      @NonNull
      FactSpec spec = FactSpec.ns("x").type("y").version(2).aggIdProperty("a", UUID.randomUUID());
      @NonNull SubscriptionRequest req = SubscriptionRequest.catchup(spec).fromScratch();
      FactFilter uut = new FactFilter(req, ef);
      assertThat(uut.canBeSkipped()).isFalse();
    }
  }
}
