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
package org.factcast.itests.factus;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.proj.SubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest
// @ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
public class LocalSubscribedLockManagementTest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;

  @SneakyThrows
  @Test
  void releasesLockOnClose() {

    SubscribedUserNames p = Mockito.spy(new SubscribedUserNames());

    Subscription s = factus.subscribeAndBlock(p);

    CompletableFuture.runAsync(
            () -> {
              Assertions.assertThrows(
                  Exception.class, () -> p.acquireWriteToken(Duration.ofMillis(300)));
            })
        .get();
  }
}
