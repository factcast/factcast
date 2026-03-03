/*
 * Copyright © 2017-2025 factcast.org
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

import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.snap.local.*;
import org.factcast.factus.*;
import org.factcast.factus.aggregate.cache.*;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projector.FactSpecProvider;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.event.*;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@ContextConfiguration(
    classes = {TestFactusApplication.class, CachedAggregateLookupITest.ExtraConfiguration.class})
@Slf4j
public class CachedAggregateLookupITest extends AbstractFactCastIntegrationTest {

  @Autowired Factus factus;
  @Autowired FactSpecProvider fsp;
  @Autowired TestAggregateCache<User> c;

  static class ExtraConfiguration {

    @Bean
    InMemorySnapshotCache inMemorySnapshotCache() {
      return new InMemorySnapshotCache(new InMemorySnapshotProperties());
    }

    @Bean
    TestAggregateCache<User> getUserAbstractAggregateCache(Factus factus, FactSpecProvider fsp) {
      TestAggregateCache<User> cache = new TestAggregateCache<User>(User.class);
      cache.start(factus, fsp);
      return cache;
    }
  }

  @Test
  void caches() {

    UUID id = new UUID(1, 2);
    assertThat(factus.find(User.class, id)).isEmpty();
    assertThat(factus.find(c, id)).isEmpty();

    factus.publish(new UserCreated(id, "Kenny"));

    assertThat(factus.find(User.class, id)).isNotEmpty();
    Optional<User> cached = factus.find(c, id);
    assertThat(cached).isNotEmpty().hasValue(factus.find(c, id).get());
  }

  @SneakyThrows
  @Test
  void invalidates() {

    UUID id = new UUID(1, 3);
    assertThat(factus.find(User.class, id)).isEmpty();
    assertThat(factus.find(c, id)).isEmpty();
    assertThat(c.get(id)).isNull();

    factus.publish(new UserCreated(id, "Kenny"));

    assertThat(factus.find(User.class, id)).isNotEmpty();
    User kenny = factus.find(c, id).orElseThrow();
    assertThat(kenny.bored()).isFalse();

    c.clearTrail();
    factus.publish(new UserBored(id));

    // wait for the cache to invalidate
    c.waitForInvalidationOf(id);
    assertThat(c.get(id)).isNull();

    User kenny2 = factus.find(c, id).orElseThrow();
    assertThat(kenny2).isNotSameAs(kenny);

    assertThat(kenny2.bored()).isTrue();
  }
}

@Getter
class User extends Aggregate {
  boolean bored = false;
  boolean deleted = false;
  String name;

  @Handler
  void apply(UserCreated e) {
    name = e.userName();
  }

  @Handler
  void apply(UserDeleted e) {
    deleted = true;
  }

  @Handler
  void apply(UserBored e) {
    bored = true;
  }
}
