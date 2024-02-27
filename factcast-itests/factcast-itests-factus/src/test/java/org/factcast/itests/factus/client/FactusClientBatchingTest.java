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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.BatchingFactObserver;
import org.factcast.factus.Factus;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.RedissonProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.proj.*;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {TestFactusApplication.class, RedissonProjectionConfiguration.class})
@Slf4j
class FactusClientBatchingTest extends AbstractFactCastIntegrationTest {
  private static final long WAIT_TIME_FOR_ASYNC_FACT_DELIVERY = 1000;

  static {
    System.setProperty("factcast.grpc.client.catchup-batchsize", "100");
  }

  @Autowired Factus factus;
  @Autowired FactCast fc;

  @Autowired EventConverter eventConverter;

  @Autowired SubscribedUserNames subscribedUserNames;

  List<List<Fact>> values = new CopyOnWriteArrayList<>();

  @Test
  void recievedBatchInCatchupPhase_SingletonsAfter() throws Exception {

    final CountDownLatch cl = new CountDownLatch(3);

    factus.publish(new UserCreated("John"));
    factus.publish(new UserCreated("Paul"));
    factus.publish(new UserCreated("George"));
    factus.publish(new UserCreated("Ringo"));

    try (Subscription subscription =
        fc.subscribe(
                SubscriptionRequest.follow(FactSpec.from(UserCreated.class))
                    .withMaxBatchDelayInMs(10)
                    .fromScratch(),
                new TestObserver(cl))
            .awaitCatchup(); ) {
      // must arrive as one array
      Assertions.assertThat(cl.getCount()).isEqualTo(2);

      factus.publish(new UserCreated("afterCatchup"));
      Thread.sleep(100);
      factus.publish(new UserCreated("afterCatchup2"));

      // we expect two singleton lists to arrive
      cl.await(5, TimeUnit.SECONDS);
      Assertions.assertThat(values.get(0)).hasSize(4);
      Assertions.assertThat(values.get(1)).hasSize(1);
      Assertions.assertThat(values.get(2)).hasSize(1);
    }
  }

  @Test
  // @Disabled("needs quite some refactoring")
  void recievedBatchInCatchupPhase_BatchedByMaxDelay() throws Exception {
    final CountDownLatch cl = new CountDownLatch(2);

    factus.publish(new UserCreated("John"));
    factus.publish(new UserCreated("Paul"));
    factus.publish(new UserCreated("George"));
    factus.publish(new UserCreated("Ringo"));

    try (Subscription subscription =
        fc.subscribe(
                SubscriptionRequest.follow(FactSpec.from(UserCreated.class))
                    .withMaxBatchDelayInMs(1000)
                    .fromScratch(),
                new TestObserver(cl))
            .awaitCatchup(); ) {
      // must arrive as one array
      Assertions.assertThat(cl.getCount()).isOne();

      factus.publish(new UserCreated("afterCatchup"));
      Thread.sleep(100);
      Assertions.assertThat(cl.getCount()).isOne();

      factus.publish(new UserCreated("afterCatchup2"));

      // we expect both to arrive in one array
      cl.await(5, TimeUnit.SECONDS);

      Assertions.assertThat(values.get(0)).hasSize(4);
      Assertions.assertThat(values.get(1)).hasSize(2);
    }
  }

  @RequiredArgsConstructor
  class TestObserver implements BatchingFactObserver {

    final CountDownLatch cl;

    @Override
    public void onNext(@NonNull List<Fact> elements) {
      values.add(elements);
      cl.countDown();
    }
  }
}
