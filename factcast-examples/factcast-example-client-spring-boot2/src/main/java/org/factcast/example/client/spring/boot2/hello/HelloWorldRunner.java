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

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.val;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class HelloWorldRunner implements CommandLineRunner {

  @NonNull private final FactCast fc;

  @Override
  public void run(String... args) throws Exception {

    //    val id = UUID.randomUUID();
    //    Fact fact =
    //        Fact.builder()
    //            .ns("users")
    //            .type("UserCreated")
    //            .version(1)
    //            .id(id)
    //            .build("{\"firstName\":\"Horst\",\"lastName\":\"Lichter\"}");
    //    fc.publish(fact);
    //    System.out.println("published " + fact);
    //
    //    val uc = fc.fetchById(id);
    //    System.out.println(uc.get().jsonPayload());
    //
    //    val uc1 = fc.fetchByIdAndVersion(id, 1);
    //    System.out.println(uc1.get().jsonPayload());
    //
    //    val uc2 = fc.fetchByIdAndVersion(id, 2);
    //    System.out.println(uc2.get().jsonPayload());
    //
    //    val uc3 = fc.fetchByIdAndVersion(id, 3);
    //    System.out.println(uc3.get().jsonPayload());
    //
    //    fc.subscribe(
    //            SubscriptionRequest.catchup(FactSpec.ns("users")).fromScratch(),
    //            element -> System.out.println(element))
    //        .awaitCatchup();

    AtomicLong l = new AtomicLong(0);
    Timer t = new Timer();
    TimerTask task =
        new TimerTask() {
          @Override
          public void run() {
            val c = l.getAndSet(0);
            System.out.println("throughput " + 6 * c + "/min");
          }
        };
    t.scheduleAtFixedRate(task, 0, 10000);

    fc.subscribe(
            SubscriptionRequest.catchup(
                    FactSpec.ns("transportauctions").type("TransportAuctionEvaluationFinished"))
                .fromScratch(),
            f -> {
              l.incrementAndGet();
            })
        .awaitCatchup();
    t.cancel();
  }
}
