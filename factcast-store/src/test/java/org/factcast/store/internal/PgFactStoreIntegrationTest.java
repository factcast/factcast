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

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import lombok.*;
import lombok.experimental.Delegate;
import org.assertj.core.util.Lists;
import org.factcast.core.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.*;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.*;
import org.factcast.store.test.AbstractFactStoreTest;
import org.factcast.test.IntegrationTest;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.*;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
class PgFactStoreIntegrationTest extends AbstractFactStoreTest {

  // see db/changelog/factcast/issue1483/augmentation_trigger_truncate_ts.sql
  static final String DB_TIME_QUERY =
      "select TRUNC(EXTRACT(EPOCH FROM now()::timestamptz(3)) * 1000);";

  @Autowired FactStore fs;

  @Autowired PgMetrics metrics;

  @Autowired TokenStore tokenStore;

  @Autowired JdbcTemplate jdbcTemplate;
  @Autowired private FastForwardTarget fastForwardTarget;

  @Override
  protected FactStore createStoreToTest() {
    return new FactStoreWrapper(fs);
  }

  /**
   * This weird trick is necessary, because Spring-Boot does something to autowired beans, so that
   * mockito cannot spy them anymore. This wrapper serves as a simple shell around the injected
   * FactStore bean, so that it can be spied as expected with mockito.spy()
   *
   * <p>And yes, I apologize...
   */
  @RequiredArgsConstructor
  private static class FactStoreWrapper implements FactStore {
    @Delegate final FactStore delegate;
  }

  /** This happens in a trigger */
  @Test
  @SneakyThrows
  void testSerialAndTimestampWereAugmented() {
    // INIT
    UUID id = UUID.randomUUID();

    // RUN
    // we need to check if the timestamp that is added to meta makes sense, hence
    // capture current millis before and after publishing, and compare against the
    // timestamp set in meta.
    //
    // Not using "System.currentTimeMillis()" because a difference (by a few millis) to DB time has
    // been observed.
    var before = fs.currentTime();

    uut.publish(Fact.builder().ns("augmentation").type("test").id(id).buildWithoutPayload());

    // ASSERT
    var fact = uut.fetchById(id);
    // fetching after here, as the trigger seems to be delayed
    var after = fs.currentTime();

    assertThat(fact).isPresent();

    String ser = fact.get().header().meta().getFirst("_ser");
    assertThat(Long.parseLong(Objects.requireNonNull(ser))).isPositive();

    String ts = fact.get().header().meta().getFirst("_ts");
    assertThat(Long.parseLong(Objects.requireNonNull(ts)))
        .isGreaterThanOrEqualTo(before)
        .isLessThanOrEqualTo(after);
  }

  @SuppressWarnings("resource")
  @Nested
  class FastForward {
    @NonNull final UUID id = UUID.randomUUID();
    @NonNull final UUID id2 = UUID.randomUUID();
    @NonNull final UUID id3 = UUID.randomUUID();
    final AtomicReference<FactStreamPosition> fwd = new AtomicReference<>();

    @NonNull
    final FactObserver obs =
        new FactObserver() {

          @Override
          public void onNext(@NonNull Fact element) {
            //
          }

          @Override
          public void onCatchup() {
            System.out.println("onCatchup");
          }

          @Override
          public void onFastForward(@NonNull FactStreamPosition factIdToFfwdTo) {
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
    }

    @Test
    void testFfwdFromScratch() {

      SubscriptionRequest scratch = SubscriptionRequest.catchup(spec).fromScratch();
      store.subscribe(SubscriptionRequestTO.from(scratch), obs).awaitCatchup();

      SubscriptionRequest tail = SubscriptionRequest.catchup(spec).from(id);
      store.subscribe(SubscriptionRequestTO.from(tail), obs).awaitCatchup();

      // now, we expect a ffwd here
      assertThat(fwd.get()).isNotNull();
    }

    @Test
    void doesNotRewind() {

      // now insert a fresh one
      store.publish(
          Collections.singletonList(Fact.builder().id(id2).ns("ns1").buildWithoutPayload()));

      SubscriptionRequest newtail = SubscriptionRequest.catchup(spec).from(id);
      store.subscribe(SubscriptionRequestTO.from(newtail), obs).awaitCatchup();

      fwd.set(null);

      // check for empty catchup
      SubscriptionRequest emptyTail = SubscriptionRequest.catchup(spec).from(id2);
      store.subscribe(SubscriptionRequestTO.from(emptyTail), obs).awaitCatchup();

      assertThat(fwd.get()).isNull();

      // check for actual catchup (must not rewind)
      store.publish(
          Collections.singletonList(Fact.builder().id(id3).ns("ns1").buildWithoutPayload()));

      SubscriptionRequest nonEmptyTail = SubscriptionRequest.catchup(spec).from(id2);
      store.subscribe(SubscriptionRequestTO.from(nonEmptyTail), obs).awaitCatchup();

      // still no ffwd because the ffwd target is smaller than id2
      assertThat(fwd.get()).isNull();
    }

    @Test
    void movedTarget() {
      spec = Collections.singletonList(FactSpec.ns("noneOfThese"));

      SubscriptionRequest mt = SubscriptionRequest.catchup(spec).fromScratch();
      store.subscribe(SubscriptionRequestTO.from(mt), obs).awaitCatchup();

      // ffwd expected
      assertThat(fwd.get()).isNotNull();
      FactStreamPosition first = fwd.get();

      // publish unrelated stuff and update ffwd target
      store.publish(
          Collections.singletonList(Fact.builder().ns("unrelated").buildWithoutPayload()));

      SubscriptionRequest further = SubscriptionRequest.catchup(spec).from(id2);
      store.subscribe(SubscriptionRequestTO.from(further), obs).awaitCatchup();

      // now it should ffwd again to the last unrelated one
      assertThat(fwd.get()).isNotNull().isNotEqualTo(first);
    }
  }

  @Test
  void getCurrentStateOnEmptyFactTableReturns0() {
    StateToken token = store.currentStateFor(Lists.newArrayList());
    assertThat(token).isNotNull();

    Optional<State> state = tokenStore.get(token);
    assertThat(state).isNotEmpty();
    assertThat(state.get()).extracting(State::serialOfLastMatchingFact).isEqualTo(0L);
  }

  @Test
  void getStateOnEmptyFactTableReturns0() {
    StateToken token = store.stateFor(Lists.newArrayList(FactSpec.ns("foo").type("bar")));
    assertThat(token).isNotNull();

    Optional<State> state = tokenStore.get(token);
    assertThat(state).isNotEmpty();
    assertThat(state.get()).extracting(State::serialOfLastMatchingFact).isEqualTo(0L);
  }

  @SuppressWarnings("deprecation")
  @Nested
  class FactStoreTest {
    final FactStore localFactStore = fs;

    @Test
    void testFetchBySerialReturnsFact() {
      UUID id = UUID.randomUUID();
      Fact fact = Fact.builder().id(id).ns("foo").type("bar").buildWithoutPayload();
      store.publish(Collections.singletonList(fact));
      long serial = store.serialOf(id).orElseThrow();

      Optional<Fact> fetchedFact = localFactStore.fetchBySerial(serial);
      assertThat(fetchedFact.orElseThrow().id()).isEqualTo(id);
    }

    @Test
    void testFetchByNonExistantSerialReturnsEmpty() {
      assertThat(localFactStore.fetchBySerial(8273648723L)).isEmpty();
    }

    @Test
    void testLatestSerialReturns() {

      // initially should be 0
      assertThat(localFactStore.latestSerial()).isZero();

      UUID id = UUID.randomUUID();
      Fact fact = Fact.builder().id(id).ns("foo").type("bar").buildWithoutPayload();
      store.publish(Collections.singletonList(fact));
      long expectedSerial = store.serialOf(id).orElseThrow();

      long serial = localFactStore.latestSerial();
      assertThat(serial).isEqualTo(expectedSerial);

      // make sure it changes
      var other = Fact.builder().ns("foo").type("bar").buildWithoutPayload();
      store.publish(Collections.singletonList(other));

      assertThat(localFactStore.latestSerial()).isEqualTo(expectedSerial + 1);
    }

    @Test
    void testLastSerialBeforeNowReturns0() {
      UUID id1 = UUID.randomUUID();
      Fact fact1 = Fact.builder().id(id1).ns("foo").type("bar").buildWithoutPayload();
      UUID id2 = UUID.randomUUID();
      Fact fact2 = Fact.builder().id(id2).ns("foo").type("bar").buildWithoutPayload();
      store.publish(Lists.newArrayList(fact1, fact2));

      long serial = localFactStore.lastSerialBefore(LocalDate.now());
      assertThat(serial).isZero();
    }

    @Test
    void testLastSerialBeforeFutureDate() {
      UUID id = UUID.randomUUID();
      Fact fact = Fact.builder().id(id).ns("foo").type("bar").buildWithoutPayload();
      store.publish(Collections.singletonList(fact));

      long ser = store.fetchById(id).get().serial();

      assertThat(localFactStore.lastSerialBefore(LocalDate.now().plusWeeks(1))).isEqualTo(ser);
    }

    @Test
    void testLastSerialBeforePastDate() {

      jdbcTemplate.update(
          "INSERT INTO " + PgConstants.TABLE_DATE2SERIAL + "(factdate,firstser) VALUES (?, ?)",
          LocalDate.now().minusWeeks(1),
          100L);

      jdbcTemplate.update(
          "INSERT INTO " + PgConstants.TABLE_DATE2SERIAL + "(factdate,firstser) VALUES (?, ?)",
          LocalDate.now().minusDays(1),
          200L);

      assertThat(localFactStore.lastSerialBefore(LocalDate.now().minusDays(1))).isEqualTo(200L - 1);
    }

    @Test
    void testFirstSerialAfterNowReturns() {
      assertThat(localFactStore.firstSerialAfter(LocalDate.now())).isNull();

      // add one to the end, should not change anything
      UUID id = UUID.randomUUID();
      Fact fact = Fact.builder().id(id).ns("foo").type("bar").buildWithoutPayload();
      store.publish(Collections.singletonList(fact));

      assertThat(localFactStore.firstSerialAfter(LocalDate.now()))
          .isEqualTo(localFactStore.latestSerial());
    }

    @SuppressWarnings({"DataFlowIssue", "OptionalGetWithoutIsPresent"})
    @Test
    void testDate2SerTriggerWorks() {
      int rows = getRowsInDate2Serial();
      assertThat(rows).isZero();

      // add one to the end, should not change anything
      UUID id = UUID.randomUUID();
      Fact fact = Fact.builder().id(id).ns("foo").type("bar").buildWithoutPayload();
      store.publish(Collections.singletonList(fact));
      var ser = store.serialOf(id).getAsLong();

      {
        int rowsAfterInsert = getRowsInDate2Serial();
        assertThat(rowsAfterInsert).isOne();

        var first = getFirstSerial(LocalDate.now());

        // ser should be first & last
        assertThat(first).isEqualTo(ser);
      }
      // add another
      UUID otherId = UUID.randomUUID();
      Fact otherFact = Fact.builder().id(otherId).ns("foo").type("bar").buildWithoutPayload();
      store.publish(Collections.singletonList(otherFact));
      var otherSer = store.serialOf(otherId).getAsLong();

      {
        // now last should have changed, first must be unchanged
        int rowsAfterInsert = getRowsInDate2Serial();
        assertThat(rowsAfterInsert).isOne();

        var first = getFirstSerial(LocalDate.now());

        // ser should be first
        assertThat(first).isEqualTo(ser);
      }
    }

    @Nullable
    private Long getFirstSerial(@NonNull LocalDate date) {
      return jdbcTemplate.queryForObject(
          "SELECT firstSer FROM " + PgConstants.TABLE_DATE2SERIAL + " WHERE factDate = ?",
          new Object[] {date},
          Long.class);
    }

    @Nullable
    private Integer getRowsInDate2Serial() {
      return jdbcTemplate.queryForObject(
          "SELECT count(*) FROM " + PgConstants.TABLE_DATE2SERIAL, Integer.class);
    }

    @SneakyThrows
    @Test
    void testMigrationAfterTriggerInstalled() {
      var today = ZonedDateTime.now();
      var weekAgo = today.minusWeeks(1);
      var yearAgo = today.minusYears(1);

      // lets assume we have facts that were inserted before the date2serial trigger was enabled
      jdbcTemplate.execute("ALTER TABLE fact DISABLE TRIGGER tr_fact_date2serial;");
      // also we want to insert explicit _ts values, so we disable the augment trigger as well
      jdbcTemplate.execute("ALTER TABLE fact DISABLE TRIGGER tr_fact_augment");
      String INSERT_FACT =
          "INSERT INTO fact(header,payload) VALUES ('{}'::jsonb || concat('{\"ns\":\"foo\",\"type\":\"bar\",\"id\":\"',?,'\"}')::jsonb || concat('{\"meta\":{\"_ts\":', extract(epoch from (now()::date - ?))*1000 ,'}}' )::jsonb  ,'{}')";
      // a year before
      jdbcTemplate.update(INSERT_FACT, UUID.randomUUID(), 365);
      // a week before
      jdbcTemplate.update(INSERT_FACT, UUID.randomUUID(), 7);
      // today
      jdbcTemplate.update(INSERT_FACT, UUID.randomUUID(), 0);
      // now date2ser should be still empty
      assertThat(getRowsInDate2Serial()).isZero();

      // now we enable the triggers again
      jdbcTemplate.execute("ALTER TABLE fact ENABLE TRIGGER tr_fact_augment;");
      jdbcTemplate.execute("ALTER TABLE fact ENABLE TRIGGER tr_fact_date2serial;");
      // remember when it was activated
      jdbcTemplate.execute(
          "CREATE TABLE IF NOT EXISTS tmp_fact_date_trigger (factDate date);INSERT INTO tmp_fact_date_trigger VALUES (now());");
      // and now add two more
      jdbcTemplate.update(INSERT_FACT, UUID.randomUUID(), 0);
      jdbcTemplate.update(INSERT_FACT, UUID.randomUUID(), 0);

      // date2Ser should now have one row with (today,4,5)
      assertThat(getRowsInDate2Serial()).isOne();
      assertThat(getFirstSerial(today.toLocalDate())).isEqualTo(4);

      // 4 is technically wrong as first (should be corrected to 3)
      // also facts 1&2 should be accounted for after migration

      // lets run the migration script
      ClassPathResource migrationScriptResource =
          new ClassPathResource(
              "db/changelog/factcast/issue2479/date2serial_for_existing_events.sql",
              this.getClass().getClassLoader());

      String migrationScript = migrationScriptResource.getContentAsString(StandardCharsets.UTF_8);
      jdbcTemplate.update(migrationScript);

      // we should now have 3 rows in the lookup table
      assertThat(getRowsInDate2Serial()).isEqualTo(3);
      // and the one for today should be correct
      assertThat(getFirstSerial(today.toLocalDate())).isEqualTo(3);
    }
  }
}
