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
package org.factcast.example.client.spring.boot2.hello;

import com.google.common.collect.Lists;
import java.util.UUID;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SpecBuilder;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast ec;

  @Override
  public void run(String... args) throws Exception {

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
    SpecBuilder follow =
        SubscriptionRequest.follow(Lists.newArrayList(FactSpec.ns("users").type("UserCreated")));
    SubscriptionRequest request = follow.fromScratch();
    ec.subscribe(request, obs).awaitComplete();
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }
}
