/*
 * Copyright © 2017-2026 factcast.org
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalSubscribedProjection;
import org.factcast.factus.projection.SubscribedProjection;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.*;
import org.factcast.itests.factus.event.film.*;
import org.factcast.itests.factus.proj.*;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactcastTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      TestFactusApplication.class,
    })
@Slf4j
@FactcastTestConfig(factcastVersion = "0.11.1")
public class FollowCompatibilityTest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;

  CountDownLatch cdl = new CountDownLatch(3);
  CountDownLatch catchup = new CountDownLatch(1);

  @SneakyThrows
  @Test
  public void subscribeWithoutMaxBatchDelayWorksAgainstVersion0_11_1() {
    factus.publish(new StarTrekCharacterCreated("Kirk"));

    SubscribedProjection names = new StarTrekNames();
    factus.subscribe(names);

    catchup.await(10, TimeUnit.SECONDS);

    factus.publish(new StarTrekCharacterCreated("Will"));
    factus.publish(new StarTrekCharacterCreated("Kira"));
    factus.publish(new StarTrekCharacterCreated("Odo"));
    factus.publish(new StarTrekCharacterCreated("7of9"));

    Assertions.assertThat(cdl.await(10, TimeUnit.SECONDS)).isTrue();
  }

  private class StarTrekNames extends LocalSubscribedProjection {
    @Handler
    public void apply(StarTrekCharacterCreated created) {
      cdl.countDown();
    }

    @Override
    public void onCatchup() {
      catchup.countDown();
    }
  }
}
