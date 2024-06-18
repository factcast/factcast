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
package org.factcast.itests.tls.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(classes = TLSApplication.class)
public class TLSClientTest extends AbstractFactCastIntegrationTest {

  @Autowired FactCast fc;

  @Test
  public void simplePublishRoundtrip() throws Exception {
    Fact fact = Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}");
    fc.publish(fact);

    try (Subscription sub =
        fc.subscribe(
                SubscriptionRequest.catchup(FactSpec.ns("smoke")).fromScratch(),
                f -> {
                  assertEquals(fact.ns(), f.ns());
                  assertEquals(fact.type(), f.type());
                  assertEquals(fact.id(), f.id());
                })
            .awaitCatchup(1000)) {
      // empty block
    }
  }
}
