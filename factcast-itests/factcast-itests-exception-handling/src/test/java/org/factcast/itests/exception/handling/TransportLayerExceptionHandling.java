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
package org.factcast.itests.exception.handling;

import java.util.UUID;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Lists;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@EnableAutoConfiguration
@ContextConfiguration(classes = {Application.class})
@Slf4j
public class TransportLayerExceptionHandling extends AbstractFactCastIntegrationTest {

  @Autowired Factus ec;

  @Autowired FactCast fc;

  @Test
  public void test() throws InterruptedException {
    ec.publish(
        createTestFact(
            UUID.randomUUID(), 1, "{\"firstName\":\"Peter1\",\"lastName\":\"Zwegert\"}"));
    ec.publish(
        createTestFact(
            UUID.randomUUID(), 1, "{\"firstName\":\"Peter2\",\"lastName\":\"Zwegert\"}"));
    ec.publish(
        createTestFact(
            UUID.randomUUID(), 1, "{\"firstName\":\"Peter3\",\"lastName\":\"Zwegert\"}"));

    @NonNull
    FactObserver obs =
        new FactObserver() {
          @Override
          public void onNext(@NonNull Fact element) {
            System.out.println(element);
          }

          @Override
          public void onCatchup() {
            System.out.println("cu");
          }

          @Override
          public void onComplete() {
            System.out.println("compl");
          }

          @Override
          public void onError(@NonNull Throwable exception) {
            System.out.println("onErr " + exception);
          }
        };
    fc.subscribe(
            SubscriptionRequest.follow(Lists.newArrayList(FactSpec.ns("users").type("UserCreated")))
                .fromScratch(),
            obs)
        .awaitCatchup();
    System.out.println("cuuuuuuuu");

    Thread.sleep(1000);

    ec.publish(
        createTestFact(
            UUID.randomUUID(), 1, "{\"firstName\":\"Peter3\",\"lastName\":\"Zwegert\"}"));
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }
}
