/*
 * Copyright © 2017-2022 factcast.org
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

import static java.util.concurrent.TimeUnit.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import io.grpc.StatusRuntimeException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.store.RetryableException;
import org.factcast.factus.Factus;
import org.factcast.factus.Handler;
import org.factcast.factus.projection.LocalManagedProjection;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.toxi.PostgresqlProxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@Slf4j
class DatabaseUnhealthyITest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  PostgresqlProxy proxy;

  @BeforeEach
  void setup() {
    log.info("Publishing");
    factus.publish(new UserCreated("dieter"));
  }

  @SneakyThrows
  @Test
  @Timeout(value = 15, unit = SECONDS)
  void testFactusGetsStuckIssue2162() {

    final var userProjection = new UserProjection();

    proxy.toxics().resetPeer("db-gone", ToxicDirection.UPSTREAM, 0);

    assertThatThrownBy(() -> factus.update(userProjection))
        .isInstanceOf(RetryableException.class)
        .hasCauseExactlyInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("UNKNOWN");
  }

  @AfterEach
  void tearDown() {
    proxy.reset();
  }

  @ProjectionMetaData(revision = 1)
  static class UserProjection extends LocalManagedProjection {

    @Handler
    void apply(UserCreated e) {
      // do something
    }
  }
}
