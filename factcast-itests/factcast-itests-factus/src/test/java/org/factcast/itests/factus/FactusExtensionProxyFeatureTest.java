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
package org.factcast.itests.factus;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.base.Stopwatch;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.util.concurrent.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.Factus;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.redis.RedisProxy;
import org.factcast.test.toxi.FactCastProxy;
import org.factcast.test.toxi.PostgresqlProxy;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@Slf4j
class FactusExtensionProxyFeatureTest extends AbstractFactCastIntegrationTest {
  public static final long LATENCY = 300L;
  @Autowired Factus factus;
  @Autowired RedissonClient redis;
  private FactCastProxy fcProxy;
  private PostgresqlProxy pgProxy;
  private RedisProxy redisProxy;

  @Test
  void fcProxyIsInjected() {
    assertThat(fcProxy).isNotNull();
  }

  @Test
  void pgProxyIsInjected() {
    assertThat(pgProxy).isNotNull();
  }

  @Test
  void redisProxyIsInjected() {
    assertThat(redisProxy).isNotNull();
  }

  void publish() {
    factus.publish(Fact.builder().ns("foo").type("bar").buildWithoutPayload());
  }

  @SneakyThrows
  @Test
  void factcastInteractionWithLatency() {
    Stopwatch sw = Stopwatch.createStarted();
    publish();
    long rtWithoutLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    fcProxy.toxics().latency("some latency", ToxicDirection.UPSTREAM, LATENCY);

    sw = Stopwatch.createStarted();
    publish();
    long rtWithLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    assertThat(rtWithoutLatency).isLessThan(LATENCY);
    assertThat(rtWithLatency).isGreaterThanOrEqualTo(LATENCY);
  }

  @SneakyThrows
  @Test
  void publishWithLatencyASecondTime() {
    factcastInteractionWithLatency(); // fails if proxy was not reset
  }

  @SneakyThrows
  @Test
  void redisInteractionWithLatency() {
    Stopwatch sw = Stopwatch.createStarted();
    redis.getBucket("foo").set("bar");
    long rtWithoutLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    redisProxy.toxics().latency("some latency", ToxicDirection.UPSTREAM, LATENCY);

    sw = Stopwatch.createStarted();
    redis.getBucket("foo").set("bar");
    long rtWithLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    assertThat(rtWithoutLatency).isLessThan(LATENCY);
    assertThat(rtWithLatency).isGreaterThanOrEqualTo(LATENCY);
  }

  @SneakyThrows
  @Test
  void redisWithLatencyASecondTime() {
    redisInteractionWithLatency(); // fails if proxy was not reset
  }
}
