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

import static org.assertj.core.api.Assertions.*;

import com.google.common.base.Stopwatch;
import eu.rekawek.toxiproxy.model.ToxicDirection;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.factus.Factus;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.toxi.FactCastProxy;
import org.factcast.test.toxi.PostgresqlProxy;
import org.junit.jupiter.api.*;
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
  @Autowired Factus factus;
  private FactCastProxy fcProxy;
  private PostgresqlProxy pgProxy;

  @Test
  void fcProxyIsInjected() {
    assertThat(fcProxy).isNotNull();
  }

  @Test
  void pgProxyIsInjected() {
    assertThat(pgProxy).isNotNull();
  }

  void publish() {
    factus.publish(Fact.builder().ns("foo").type("bar").buildWithoutPayload());
  }

  @SneakyThrows
  @Test
  void publishWithLatency() {
    Stopwatch sw = Stopwatch.createStarted();
    publish();
    long rtWithoutLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    fcProxy.toxics().latency("one second latency", ToxicDirection.UPSTREAM, 1000L);

    sw = Stopwatch.createStarted();
    publish();
    long rtWithLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    assertThat(rtWithoutLatency).isLessThan(1000L);
    assertThat(rtWithLatency).isGreaterThan(1000L);
  }

  @SneakyThrows
  @Test
  void publishWithLatencyASecondTime() {
    Stopwatch sw = Stopwatch.createStarted();
    publish();
    long rtWithoutLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    fcProxy.toxics().latency("one second latency", ToxicDirection.UPSTREAM, 1000L);

    sw = Stopwatch.createStarted();
    publish();
    long rtWithLatency = sw.stop().elapsed(TimeUnit.MILLISECONDS);

    assertThat(rtWithoutLatency)
        .isLessThan(1000L); // will fail if latency was not reset between tests
    assertThat(rtWithLatency).isGreaterThan(1000L);
  }
}
