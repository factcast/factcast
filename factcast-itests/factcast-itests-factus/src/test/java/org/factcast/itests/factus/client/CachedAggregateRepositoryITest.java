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

import java.util.UUID;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.core.snap.local.*;
import org.factcast.factus.*;
import org.factcast.factus.aggregates.*;
import org.factcast.factus.aggregates.cached.CachedAggregateRepository;
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
@ContextConfiguration(classes = {TestFactusApplication.class, RepoConfiguration.class})
@Slf4j
public class CachedAggregateRepositoryITest extends AbstractFactCastIntegrationTest {

  UserId id = new UserId(new UUID(1, 2));

  @Autowired Factus factus;
  @Autowired UserRepository repo;
  @Autowired CachedUserRepository cachedRepo;

  @Test
  void caches() {

    Assertions.assertThat(repo.find(id)).isEmpty();
    Assertions.assertThat(cachedRepo.find(id)).isEmpty();

    factus.publish(new UserCreated(id.getId(), "Kenny"));

    Assertions.assertThat(repo.find(id)).isNotEmpty();
    Assertions.assertThat(cachedRepo.find(id)).isNotEmpty();
    Assertions.assertThat(cachedRepo.findCached(id)).isNotEmpty();

    Assertions.assertThat(repo.find(id)).isNotSameAs(repo.find(id));
    Assertions.assertThat(cachedRepo.findCached(id)).isSameAs(cachedRepo.findCached(id));
  }
}

class User extends Aggregate {
  boolean bored = false;
  private boolean deleted = false;
  private String name;

  @Handler
  void apply(UserCreated e) {
    name = e.userName();
  }

  @Handler
  void apply(UserDeleted e) {
    deleted = true;
  }

  @Handler
  void apply(UserBored e) {}
}

@Value
class UserId implements AggregateIdentifier {
  @Accessors(fluent = false)
  UUID id;
}

class UserRepository extends AggregateRepositoryImpl<UserId, User> {
  public UserRepository(Class<User> aggregateClass, Factus factus) {
    super(aggregateClass, factus);
  }
}

class CachedUserRepository extends CachedAggregateRepository<UserId, User> {
  public CachedUserRepository(
      @NonNull AggregateRepository<UserId, User> delegate,
      @NonNull Factus factus,
      @NonNull FactSpecProvider factSpecProvider) {
    super(delegate, factus, factSpecProvider);
  }
}

@Configuration
class RepoConfiguration {
  @Bean
  UserRepository userRepository(Factus f) {
    return new UserRepository(User.class, f);
  }

  @Bean
  CachedUserRepository cachedUserRepository(UserRepository r, Factus f, FactSpecProvider p) {
    return new CachedUserRepository(r, f, p);
  }

  @Bean
  InMemorySnapshotCache inMemorySnapshotCache() {
    return new InMemorySnapshotCache(new InMemorySnapshotProperties());
  }
}
