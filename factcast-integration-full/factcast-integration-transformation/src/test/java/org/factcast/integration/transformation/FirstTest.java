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
package org.factcast.integration.transformation;

import static org.junit.jupiter.api.Assertions.*;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.FactValidationException;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest
public class FirstTest {

    @Autowired
    FactCast fc;

    @Test
    public void simplePublishRoundtrip() throws Exception {
        Fact brokenFact = Fact.builder()
                .ns("Users")
                .type("UserCreated")
                .version(1)
                .build("{\"bla\":\"fasel\"}");
        assertThrows(FactValidationException.class, () -> {
            fc.publish(brokenFact);
        });

        Fact properFact = Fact.builder()
                .ns("Users")
                .type("UserCreated")
                .version(1)
                .build("{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}");
        fc.publish(properFact);

        try (Subscription sub = fc.subscribe(SubscriptionRequest.follow(FactSpec.ns("Users"))
                .fromScratch(),
                f -> {
                    assertEquals(properFact.ns(), f.ns());
                    assertEquals(properFact.type(), f.type());
                    assertEquals(properFact.id(), f.id());
                }).awaitCatchup(1000);) {
            // empty block
        }
    }
}
