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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;

import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.factcast.core.Fact;
import org.factcast.core.snap.Snapshot;
import org.factcast.core.snap.SnapshotId;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.StoreMetrics.OP;
import org.factcast.store.internal.tail.PGTailIndexManager;
import org.factcast.store.test.AbstractFactStoreTest;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import lombok.NonNull;
import lombok.SneakyThrows;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgFactStoreTest extends AbstractFactStoreTest {

  @Autowired FactStore fs;

  @Autowired PgMetrics metrics;

  @Autowired PGTailIndexManager tailManager;

  @Override
  protected FactStore createStoreToTest() {
    return fs;
  }

  @Test
  void testGetSnapshotMetered() {
    Optional<Snapshot> snapshot = store.getSnapshot(SnapshotId.of("xxx", UUID.randomUUID()));
    assertThat(snapshot).isEmpty();

    verify(metrics).time(same(OP.GET_SNAPSHOT), any(Supplier.class));
  }

  @Test
  void testClearSnapshotMetered() {
    var id = SnapshotId.of("xxx", UUID.randomUUID());
    store.clearSnapshot(id);
    verify(metrics).time(same(OP.CLEAR_SNAPSHOT), any(Runnable.class));
  }

  @Test
  void testSetSnapshotMetered() {
    var id = SnapshotId.of("xxx", UUID.randomUUID());
    var snap = new Snapshot(id, UUID.randomUUID(), "foo".getBytes(), false);
    store.setSnapshot(snap);

    verify(metrics).time(same(OP.SET_SNAPSHOT), any(Runnable.class));
  }

  /** This happens in a trigger */
  @Test
  @SneakyThrows
  void testSerialAndTimestampWereAugmented() {
    // INIT
    UUID id = UUID.randomUUID();

    // RUN
    // we need to check if the timestamp that is added to meta makes sense, hence
    // capture current millis before and after publishing, and compare against the timestamp
    // set in meta.
    var before = System.currentTimeMillis();
    uut.publish(Fact.builder().ns("augmentation").type("test").id(id).buildWithoutPayload());

    // ASSERT
    var fact = uut.fetchById(id);
    // fetching after here, as the trigger seems to be delayed
    var after = System.currentTimeMillis();

    assertThat(fact).isPresent();

    assertThat(Long.parseLong(fact.get().meta("_ser"))).isGreaterThanOrEqualTo(1);

    assertThat(Long.parseLong(fact.get().meta("_ts")))
        .isGreaterThanOrEqualTo(before)
        .isLessThanOrEqualTo(after);
  }

  @Nested
  class FastForward {
    @NonNull UUID id = UUID.randomUUID();
    @NonNull UUID id2 = UUID.randomUUID();
    @NonNull UUID id3 = UUID.randomUUID();
    AtomicReference<UUID> fwd = new AtomicReference<>();
    private long lastSer = 0L;

    @NonNull
    FactObserver obs =
        new FactObserver() {

          @Override
          public void onNext(@NonNull Fact element) {
            lastSer = element.serial();
          }

          @Override
          public void onCatchup() {
            System.out.println("onCatchup");
          }

          @Override
          public void onFastForward(UUID factIdToFfwdTo) {
            fwd.set(factIdToFfwdTo);
            System.out.println("ffwd " + factIdToFfwdTo);
          }
        };

    @NonNull Collection<FactSpec> spec = Collections.singletonList(FactSpec.ns("ns1"));

    @BeforeEach
    void setup() {
      store.publish(
          Collections.singletonList(Fact.builder().id(id).ns("ns1").buildWithoutPayload()));
      // have some more facts in the database
      store.publish(
          Collections.singletonList(Fact.builder().ns("unrelated").buildWithoutPayload()));
      // update the highwatermarks
      tailManager.triggerTailCreation();
    }

    @Test
    void testFfwdFromScratch() {

      SubscriptionRequest scratch = SubscriptionRequest.catchup(spec).fromScratch();
      store.subscribe(SubscriptionRequestTO.forFacts(scratch), obs).awaitCatchup();

      SubscriptionRequest tail = SubscriptionRequest.catchup(spec).from(id);
      store.subscribe(SubscriptionRequestTO.forFacts(tail), obs).awaitCatchup();

      // now, we expect a ffwd here
      assertThat(fwd.get()).isNotNull();
    }

    @Test
    void doesNotRewind() {

      // now insert a fresh one
      store.publish(
          Collections.singletonList(Fact.builder().id(id2).ns("ns1").buildWithoutPayload()));

      SubscriptionRequest newtail = SubscriptionRequest.catchup(spec).from(id);
      store.subscribe(SubscriptionRequestTO.forFacts(newtail), obs).awaitCatchup();

      tailManager.triggerTailCreation();
      fwd.set(null);

      // check for empty catchup
      SubscriptionRequest emptyTail = SubscriptionRequest.catchup(spec).from(id2);
      store.subscribe(SubscriptionRequestTO.forFacts(emptyTail), obs).awaitCatchup();

      assertThat(fwd.get()).isNull();

      // check for actual catchup (must not rewind)
      store.publish(
          Collections.singletonList(Fact.builder().id(id3).ns("ns1").buildWithoutPayload()));

      SubscriptionRequest nonEmptyTail = SubscriptionRequest.catchup(spec).from(id2);
      store.subscribe(SubscriptionRequestTO.forFacts(nonEmptyTail), obs).awaitCatchup();

      // still no ffwd because the ffwd target is smaller than id2
      assertThat(fwd.get()).isNull();
    }

    @Test
    void movedTarget() {
      spec = Collections.singletonList(FactSpec.ns("noneOfThese"));

      SubscriptionRequest mt = SubscriptionRequest.catchup(spec).fromScratch();
      store.subscribe(SubscriptionRequestTO.forFacts(mt), obs).awaitCatchup();

      // ffwd expected
      assertThat(fwd.get()).isNotNull();
      UUID first = fwd.get();

      // publish unrelated stuff and update ffwd target
      store.publish(
          Collections.singletonList(Fact.builder().ns("unrelated").buildWithoutPayload()));
      tailManager.triggerTailCreation();

      SubscriptionRequest further = SubscriptionRequest.catchup(spec).from(id2);
      store.subscribe(SubscriptionRequestTO.forFacts(further), obs).awaitCatchup();

      // now it should ffwd again to the last unrelated one
      assertThat(fwd.get()).isNotNull().isNotEqualTo(first);
    }
  }
}
