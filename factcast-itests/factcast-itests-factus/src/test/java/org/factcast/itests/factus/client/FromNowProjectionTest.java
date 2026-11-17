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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.*;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.event.*;
import org.factcast.itests.factus.event.film.*;
import org.factcast.itests.factus.proj.*;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FromNowProjectionTest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired FactCast fc;

  @SneakyThrows
  @Test
  void subscribedProjection() {
    var proj = new FromNowSubscribedProjection();
    var u1 = new UserCreated(UUID.randomUUID(), "tommy.goodman");
    var u2 = new UserCreated(UUID.randomUUID(), "stan.goodman");
    var e1Id = factus.publish(u1, Fact::id);
    blockUntilVisible(e1Id);

    factus.subscribeAndBlock(proj).awaitCatchup();

    var e2Id = factus.publish(u2, Fact::id);
    factus.waitFor(proj, e2Id, Duration.ofSeconds(5));
    assertThat(proj.userNames()).containsOnlyKeys(u2.aggregateId());
  }

  @SneakyThrows
  @Test
  void subscribedProjectionAfterFastForward() {
    var proj = new FromNowSubscribedProjection();
    var eId =
        factus.publish(
            List.of(new UserBored(), new UserBored(), new UserBored(), new UserBored()),
            f -> f.getLast().id());
    blockUntilVisible(eId);

    factus.subscribeAndBlock(proj).awaitCatchup();

    // after initial ffwd the position should not be null
    assertThat(proj.factStreamPosition()).isNotNull();
  }

  @SneakyThrows
  @Test
  void managedProjection() {
    var proj = new FromNowManagedProjection();
    var u1 = new UserCreated(UUID.randomUUID(), "tommy.goodman");
    var u2 = new UserCreated(UUID.randomUUID(), "stan.goodman");
    var e1Id = factus.publish(u1, Fact::id);
    blockUntilVisible(e1Id);
    // this first update should set the projection fact stream position at "now"
    factus.update(proj);
    // this new event should then be consumed on the next update
    var e2Id = factus.publish(u2, Fact::id);
    blockUntilVisible(e2Id);

    factus.update(proj);

    assertThat(proj.userNames()).containsOnlyKeys(u2.aggregateId());
  }

  // blocks until we are sure that the horizon is updated
  private void blockUntilVisible(UUID factId) {
    await().atMost(Duration.ofSeconds(5)).until(() -> fc.fetchById(factId).isPresent());
  }
}
