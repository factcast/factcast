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
package org.factcast.store.internal.blacklist;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class ResourceBlacklistIntegrationTest {

  @Autowired FactStore fs;
  private final UUID blockedFactId1 = UUID.fromString("2f9d0632-809a-43be-ac9b-d5100e330de7");
  private final UUID blockedFactId2 = UUID.fromString("b7a575ba-a4da-45f4-a205-7e6e424d2d64");
  private final UUID factId = UUID.fromString("d6554917-5063-4ffb-a184-4e0e46de3218");
  private final Collection<FactSpec> spec = Collections.singletonList(FactSpec.ns("ns1"));
  final Set<UUID> receivedFactIds = new HashSet<>();
  final FactObserver obs = element -> receivedFactIds.add(element.id());

  @BeforeAll
  static void configureBlacklistType() {
    System.setProperty("factcast.blacklist.type", "RESOURCE");
  }

  @BeforeEach
  void setup() {
    fs.publish(
        List.of(
            Fact.builder().id(blockedFactId1).ns("ns1").buildWithoutPayload(),
            Fact.builder().id(factId).ns("ns1").buildWithoutPayload(),
            Fact.builder().id(blockedFactId2).ns("ns1").buildWithoutPayload()));
  }

  @Test
  void blacklistIsApplied() {
    SubscriptionRequest req = SubscriptionRequest.catchup(spec).fromScratch();
    fs.subscribe(SubscriptionRequestTO.forFacts(req), obs).awaitCatchup();
    assertThat(receivedFactIds).hasSize(1).containsExactly(factId);
  }
}
