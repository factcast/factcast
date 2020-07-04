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
package org.factcast.example.tls.client;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TLSClientTest {
    @ClassRule
    public static DockerComposeContainer<?> compose = new DockerComposeContainer<>(
            new File("docker-compose.yml"))
                    .withExposedService("db", 5432, new HostPortWaitStrategy())
                    .withExposedService("factcast", 9443,
                            new HostPortWaitStrategy());

    @Autowired
    FactCast fc;

    @Test
    public void simplePublishRoundtrip() throws Exception {
        Fact fact = Fact.builder().ns("smoke").type("foo").build("{\"bla\":\"fasel\"}");
        fc.publish(fact);

        try (Subscription sub = fc.subscribe(SubscriptionRequest.follow(FactSpec.ns("smoke"))
                .fromScratch(),
                f -> {
                    assertEquals(fact.ns(), f.ns());
                    assertEquals(fact.type(), f.type());
                    assertEquals(fact.id(), f.id());
                }).awaitCatchup(1000);) {
            // empty block
        }
    }
}
