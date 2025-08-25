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

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import nl.altindag.log.LogCaptor;
import org.factcast.core.FactStreamPosition;
import org.factcast.factus.Factus;
import org.factcast.factus.FactusImpl;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.projection.WriterToken;
import org.factcast.factus.serializer.ProjectionMetaData;
import org.factcast.itests.TestFactusApplication;
import org.factcast.itests.factus.config.MongoDbProjectionConfiguration;
import org.factcast.itests.factus.event.UserCreated;
import org.factcast.itests.factus.event.UserDeleted;
import org.factcast.itests.factus.proj.MongoDbManagedUserNames;
import org.factcast.itests.factus.proj.MongoDbSubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.org.awaitility.Awaitility;

@SpringBootTest
@ContextConfiguration(classes = {TestFactusApplication.class, MongoDbProjectionConfiguration.class})
@Slf4j
public class MongoITest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;
  @Autowired MongoDatabase mongoDb;
  static final int NUMBER_OF_EVENTS = 10;

  @BeforeEach
  void setup() {
    var l = new ArrayList<EventObject>(NUMBER_OF_EVENTS);
    for (int i = 0; i < NUMBER_OF_EVENTS; i++) {
      l.add(new UserCreated(randomUUID(), getClass().getSimpleName() + ":" + i));
    }
    log.info("publishing {} Events ", NUMBER_OF_EVENTS);
    factus.publish(l);
  }

  @Nested
  class Managed {
    @SneakyThrows
    @Test
    void happyPath() {
      ManagedUserNames p = new ManagedUserNames(mongoDb);
      factus.update(p);

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(10);
    }

    @SneakyThrows
    @Test
    void projectionStoppedAtException() {
      ManagedUserNamesSizeBlowAt7th p = new ManagedUserNamesSizeBlowAt7th(mongoDb);

      assertThat(p.count()).isZero();

      try {
        factus.update(p);
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(p.count()).isEqualTo(6);
      assertThat(p.stateModifications()).isEqualTo(6);
    }
  }

  @Nested
  class Subscribed {
    @SneakyThrows
    @Test
    void happyPath() {
      SubscribedUserNames p = new SubscribedUserNames(mongoDb);
      factus.subscribeAndBlock(p).awaitCatchup().close();

      assertThat(p.count()).isEqualTo(NUMBER_OF_EVENTS);
      assertThat(p.stateModifications()).isEqualTo(10);
    }

    @SneakyThrows
    @Test
    void projectionStoppedAtException() {
      // throws while applying the 7th event.
      SubscribedUserNamesSizeBlowAt7th p = new SubscribedUserNamesSizeBlowAt7th(mongoDb);

      assertThat(p.count()).isZero();

      try {
        factus.subscribeAndBlock(p).awaitCatchup().close();
      } catch (Throwable expected) {
        // ignore
      }

      assertThat(p.count()).isEqualTo(6);
      assertThat(p.stateModifications()).isEqualTo(6);
    }

    @Test
    void testTokenReleaseAfterTooManyFailures() throws Exception {
      var subscribedUserNames = new SubscribedUserNamesTokenExposedAndThrowsError(mongoDb);

      factus.publish(new UserDeleted(UUID.randomUUID()));

      assertThat(subscribedUserNames.token()).isNull();
      try (var logCaptor = LogCaptor.forClass(FactusImpl.class)) {
        logCaptor.setLogLevelToTrace();
        factus.subscribe(subscribedUserNames);

        // The projection acquired a token and
        subscribedUserNames.latch.await();
        Awaitility.await()
            .until(
                () ->
                    !logCaptor.getTraceLogs().isEmpty()
                        && logCaptor
                            .getTraceLogs()
                            .get(0)
                            .contains(
                                "Closing AutoCloseable for class class org.factcast.factus.mongodb.MongoDbWriterToken"));
      }

      Awaitility.await()
          .until(
              () -> subscribedUserNames.token() != null && !subscribedUserNames.token().isValid());
    }
  }

  // Managed Test Projections
  @ProjectionMetaData(revision = 1)
  static class ManagedUserNames extends TrackingMongoDbManagedUserNames {
    public ManagedUserNames(MongoDatabase mongoDb) {
      super(mongoDb);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class ManagedUserNamesSizeBlowAt7th extends TrackingMongoDbManagedUserNames {
    private int count;

    public ManagedUserNamesSizeBlowAt7th(MongoDatabase mongoDb) {
      super(mongoDb);
    }

    @Override
    public void apply(UserCreated created) {
      if (++count == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created);
    }
  }

  @Getter
  static class TrackingMongoDbManagedUserNames extends MongoDbManagedUserNames {
    public TrackingMongoDbManagedUserNames(MongoDatabase mongoDb) {
      super(mongoDb);
    }

    int stateModifications;

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }

  // Subscribed Test Projections
  @ProjectionMetaData(revision = 1)
  static class SubscribedUserNames extends TrackingMongoDbSubscribedUserNames {
    public SubscribedUserNames(MongoDatabase mongoDb) {
      super(mongoDb);
    }
  }

  @ProjectionMetaData(revision = 1)
  static class SubscribedUserNamesSizeBlowAt7th extends TrackingMongoDbSubscribedUserNames {
    private int count;

    public SubscribedUserNamesSizeBlowAt7th(MongoDatabase mongoDb) {
      super(mongoDb);
    }

    @Override
    public void apply(UserCreated created) {
      if (++count == 7) { // blow the second bulk
        throw new IllegalStateException("Bad luck");
      }
      super.apply(created);
    }
  }

  @Getter
  @ProjectionMetaData(revision = 1)
  static class SubscribedUserNamesTokenExposedAndThrowsError
      extends TrackingMongoDbSubscribedUserNames {
    private final CountDownLatch latch = new CountDownLatch(1);
    private WriterToken token;

    public SubscribedUserNamesTokenExposedAndThrowsError(MongoDatabase mongoDb) {
      super(mongoDb);
    }

    @Override
    public WriterToken acquireWriteToken(@NonNull Duration maxWait) {
      token = super.acquireWriteToken(maxWait);
      latch.countDown();
      return token;
    }

    @Override
    public void apply(UserDeleted created) {
      throw new IllegalArgumentException("user should be in map but wasnt");
    }
  }

  @Getter
  static class TrackingMongoDbSubscribedUserNames extends MongoDbSubscribedUserNames {
    public TrackingMongoDbSubscribedUserNames(MongoDatabase mongoDb) {
      super(mongoDb);
    }

    int stateModifications;

    @Override
    public void factStreamPosition(@NonNull FactStreamPosition factStreamPosition) {
      stateModifications++;
      super.factStreamPosition(factStreamPosition);
    }
  }
}
