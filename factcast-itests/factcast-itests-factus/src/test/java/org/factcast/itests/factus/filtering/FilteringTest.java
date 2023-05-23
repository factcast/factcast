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
package org.factcast.itests.factus.filtering;

import static java.util.UUID.*;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Maps;
import org.factcast.core.event.EventConverter;
import org.factcast.factus.Factus;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.proj.LocalUserNamesFilterByAggregateId;
import org.factcast.itests.factus.proj.LocalUserNamesFilterByMeta;
import org.factcast.itests.factus.proj.LocalUserNamesFilterByScript;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(classes = TestFactusApplication.class)
@Slf4j
public class FilteringTest extends AbstractFactCastIntegrationTest {
  private static final long WAIT_TIME_FOR_ASYNC_FACT_DELIVERY = 1000;

  @Autowired Factus factus;
  @Autowired EventConverter eventConverter;
  final LocalUserNamesFilterByMeta localUserNamesFilterByMeta = new LocalUserNamesFilterByMeta();

  final LocalUserNamesFilterByAggregateId localUserNamesFilterByAggregateId =
      new LocalUserNamesFilterByAggregateId();

  final LocalUserNamesFilterByScript localUserNamesFilterByScript =
      new LocalUserNamesFilterByScript();

  @Test
  public void filtersByScript() {

    UserCreated john = new UserCreated(randomUUID(), "John");
    UserCreated ringo = new UserCreated(randomUUID(), "Ringo");
    UserCreated paul = new UserCreated(randomUUID(), "Paul");
    // not included
    UserCreated george = new UserCreated(randomUUID(), "George");

    factus.publish(john);
    factus.publish(paul);
    factus.publish(george);
    factus.publish(ringo);

    factus.update(localUserNamesFilterByScript);

    assertThat(localUserNamesFilterByScript.count()).isEqualTo(1);
    assertThat(localUserNamesFilterByScript.contains("George")).isTrue();
  }

  @Test
  public void filtersByMeta() {

    UserCreated john =
        new UserCreated(randomUUID(), "John") {
          @Override
          public Map<String, String> additionalMetaMap() {
            return Maps.newHashMap("type", "customer");
          }
        };
    UserCreated ringo =
        new UserCreated(randomUUID(), "Ringo") {
          @Override
          public Map<String, String> additionalMetaMap() {
            // not included
            return Maps.newHashMap("type", "vip");
          }
        };
    UserCreated paul =
        new UserCreated(randomUUID(), "Paul") {
          @Override
          public Map<String, String> additionalMetaMap() {
            Map<String, String> m = Maps.newHashMap("type", "customer");
            m.put("vip", "true");
            return m;
          }
        };
    // not included
    UserCreated george = new UserCreated(randomUUID(), "George");

    factus.publish(john);
    factus.publish(paul);
    factus.publish(george);
    factus.publish(ringo);

    factus.update(localUserNamesFilterByMeta);

    assertThat(localUserNamesFilterByMeta.count()).isEqualTo(1);
    assertThat(localUserNamesFilterByMeta.contains("Paul")).isTrue();
  }

  @Test
  public void filtersByAggregateId() {

    UUID johnsId = new UUID(12, 13);
    UserCreated john =
        new UserCreated(johnsId, "John") {
          @Override
          public Map<String, String> additionalMetaMap() {
            return Maps.newHashMap("type", "customer");
          }
        };
    UserCreated ringo =
        new UserCreated(randomUUID(), "Ringo") {
          @Override
          public Map<String, String> additionalMetaMap() {
            // not included
            return Maps.newHashMap("type", "vip");
          }
        };
    UserCreated paul =
        new UserCreated(randomUUID(), "Paul") {
          @Override
          public Map<String, String> additionalMetaMap() {
            Map<String, String> m = Maps.newHashMap("type", "customer");
            m.put("vip", "true");
            return m;
          }
        };
    // not included
    UserCreated george = new UserCreated(randomUUID(), "George");

    factus.publish(john);
    factus.publish(paul);
    factus.publish(george);
    factus.publish(ringo);

    factus.update(localUserNamesFilterByAggregateId);

    assertThat(localUserNamesFilterByAggregateId.count()).isEqualTo(1);
    assertThat(localUserNamesFilterByAggregateId.contains("John")).isTrue();
  }
}
