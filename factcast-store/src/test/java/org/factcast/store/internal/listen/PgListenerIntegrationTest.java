package org.factcast.store.internal.listen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.factcast.store.internal.PgConstants.CHANNEL_FACT_INSERT;
import static org.factcast.store.internal.PgConstants.LISTEN_SQL;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.PgTestConfiguration;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.postgresql.PGNotification;
import org.postgresql.jdbc.PgConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import lombok.Getter;
import lombok.SneakyThrows;

@ContextConfiguration(classes = {PgTestConfiguration.class})
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
class PgListenerIntegrationTest {

  @Nested
  class FactInsertTrigger {

    @Autowired PgConnectionSupplier pgConnectionSupplier;
    @Autowired EventBus eventBus;
    @Autowired FactStore factStore;

    @AfterEach
    @SneakyThrows
    void unregisterListener() {
      registerTestAsListener(pgConnectionSupplier.get(), "UNLISTEN " + CHANNEL_FACT_INSERT);
    }

    @Test
    @SneakyThrows
    void containsTransactionId() {
      // INIT
      var pc = pgConnectionSupplier.get();

      // let us also register as LISTENER
      registerTestAsListener(pc, LISTEN_SQL);

      // also register on event bus
      var events = new EventCollector();
      eventBus.register(events);

      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      UUID id3 = UUID.randomUUID();

      // RUN
      // publish together, should have same tx id
      factStore.publish(
          List.of(
              Fact.builder().ns("test").type("listenerTest1").id(id1).buildWithoutPayload(),
              Fact.builder().ns("test").type("listenerTest1").id(id2).buildWithoutPayload()));
      // separate, should have another tx id
      factStore.publish(
          List.of(Fact.builder().ns("test").type("listenerTest2").id(id3).buildWithoutPayload()));

      // ASSERT
      // first, check trigger
      var notifications = pc.getNotifications(5_000);

      assertThat(notifications)
          .extracting(PGNotification::getName)
          .allMatch(CHANNEL_FACT_INSERT::equals);

      // Below, we will check that all three inserts were communicated via the postgres channel.
      // We will also extract the transaction id from all three notifications, and check, whether
      // the transaction ids for the listenerTest1 (two events published within one transaction)
      // have the same id, while the id for listenerTest2 should be different.
      // We use AtomicLong here, in order to return the tx id from the lambda.
      AtomicLong txId1a = new AtomicLong();
      AtomicLong txId1b = new AtomicLong();
      AtomicLong txId2 = new AtomicLong();

      assertThat(notifications)
          .extracting(n -> FactCastJson.readTree(n.getParameter()))
          .hasSize(3)
          .anySatisfy(
              n -> {
                var h = n.get("header");
                assertThat(h.get("ns").asText()).isEqualTo("test");
                assertThat(h.get("type").asText()).isEqualTo("listenerTest1");
                assertThat(h.get("id").asText()).isEqualTo(id1.toString().toLowerCase());
                assertThat(n.get("txId").asLong(-1)).isGreaterThanOrEqualTo(0);
                // if we reach this point, we know it is the first event, store tx id
                txId1a.set(n.get("txId").asLong());
              })
          .anySatisfy(
              n -> {
                var h = n.get("header");
                assertThat(h.get("ns").asText()).isEqualTo("test");
                assertThat(h.get("type").asText()).isEqualTo("listenerTest1");
                assertThat(h.get("id").asText()).isEqualTo(id2.toString().toLowerCase());
                assertThat(n.get("txId").asLong(-1)).isGreaterThanOrEqualTo(0);
                // if we reach this point, we know it is the second event, store tx id
                txId1b.set(n.get("txId").asLong());
              })
          .anySatisfy(
              n -> {
                var h = n.get("header");
                assertThat(h.get("ns").asText()).isEqualTo("test");
                assertThat(h.get("type").asText()).isEqualTo("listenerTest2");
                assertThat(h.get("id").asText()).isEqualTo(id3.toString().toLowerCase());
                assertThat(n.get("txId").asLong(-1)).isGreaterThanOrEqualTo(0);
                // if we reach this point, we know it is the third event, store tx id
                txId2.set(n.get("txId").asLong());
              });

      // facts published together should have same tx id
      assertThat(txId1a.get()).isEqualTo(txId1b.get());
      // facts published separately should have different tx ids
      assertThat(txId1a.get()).isNotEqualTo(txId2.get());

      // now, check events from event bus against tx ids obtained from notifications
      // here we have deduplication, so for the first two facts published together,
      // we should only have one event here:
      assertThat(events.signals())
          .extracting(PgListener.Signal::name, PgListener.Signal::ns, PgListener.Signal::type)
          .containsExactlyInAnyOrder(
              tuple(CHANNEL_FACT_INSERT, "test", "listenerTest1"),
              tuple(CHANNEL_FACT_INSERT, "test", "listenerTest2"));

      assertThat(events.signals())
          .extracting(s -> Long.parseLong(s.txId()))
          .containsExactlyInAnyOrder(txId1a.get(), txId2.get());
    }

    public class EventCollector {
      @Getter List<PgListener.Signal> signals = new ArrayList<>();

      @Subscribe
      public void onEvent(PgListener.Signal ev) {
        signals.add(ev);
      }
    }

    private void registerTestAsListener(PgConnection pc, String listenSql) throws SQLException {
      try (var ps = pc.prepareStatement(listenSql)) {
        ps.execute();
      }
    }
  }
}
