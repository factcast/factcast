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
package org.factcast.itests.factus.client;

import static org.assertj.core.api.Assertions.assertThat;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.util.*;
import java.util.concurrent.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.*;
import org.factcast.factus.projection.LocalSubscribedProjection;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.toxi.FactCastProxy;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.test.context.*;

@SpringBootTest
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestPropertySource(properties = {"factcast.grpc.client.max-inbound-message-size=512"})
class ExceptionOnHandshakeTest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;
  private FactCastProxy fcProxy;

  void publish() {
    factus.publish(Fact.builder().ns("foo").type("bar").build(randomPayload()));
  }

  private String randomPayload() {
    long l1 = 0, l2 = 0;
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 10; i++) {
      sb.append(new UUID(l1++, l2++));
    }
    return "{\"x\":\"" + sb + "\"}";
  }

  @SneakyThrows
  @Test
  void failToReconnectTerminatesSubscription() {

    System.out.println("preparing data");
    for (int i = 0; i < 10; i++) {
      publish();
    }
    System.out.println("preparing data done");

    SlowConsumer slowConsumer = new SlowConsumer();
    Subscription sub = factus.subscribeAndBlock(slowConsumer);
    slowConsumer.latch.await(); // wait for 3 facts to arrive

    // will repeatedly cause a StatusRE
    fcProxy.toxics().timeout("to", ToxicDirection.UPSTREAM, 512);

    // should propagate exception
    Assertions.assertThatThrownBy(sub::awaitComplete).isInstanceOf(Exception.class);
  }

  @SneakyThrows
  @Test
  @Order(2)
  void fcProxyWasReset() {
    assertThat(fcProxy.get().toxics().getAll()).isEmpty();
  }

  static class SlowConsumer extends LocalSubscribedProjection {

    CountDownLatch latch = new CountDownLatch(3);
    private int count;

    @SneakyThrows
    @HandlerFor(ns = "foo", type = "bar")
    void onNext(Fact f) {
      Thread.sleep(500);
      latch.countDown();
      System.out.println("processed fact " + ++count);
    }
  }
}
