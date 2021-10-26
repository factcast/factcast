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

      // RUN
      factStore.publish(
          List.of(Fact.builder().ns("test").type("listenerTest").id(id1).buildWithoutPayload()));
      factStore.publish(
          List.of(Fact.builder().ns("test").type("listenerTest").id(id2).buildWithoutPayload()));

      // ASSERT
      // first, check trigger
      var notifications = pc.getNotifications(5_000);

      assertThat(notifications)
          .extracting(PGNotification::getName)
          .allMatch(CHANNEL_FACT_INSERT::equals);

      AtomicLong txId1 = new AtomicLong();
      AtomicLong txId2 = new AtomicLong();

      assertThat(notifications)
          .extracting(n -> FactCastJson.readTree(n.getParameter()))
          .hasSize(2)
          .anySatisfy(
              n -> {
                var h = n.get("header");
                assertThat(h.get("ns").asText()).isEqualTo("test");
                assertThat(h.get("type").asText()).isEqualTo("listenerTest");
                assertThat(h.get("id").asText()).isEqualTo(id1.toString().toLowerCase());
                assertThat(n.get("txId").asLong(-1)).isGreaterThanOrEqualTo(0);
                txId1.set(n.get("txId").asLong());
              })
          .anySatisfy(
              n -> {
                var h = n.get("header");
                assertThat(h.get("ns").asText()).isEqualTo("test");
                assertThat(h.get("type").asText()).isEqualTo("listenerTest");
                assertThat(h.get("id").asText()).isEqualTo(id2.toString().toLowerCase());
                assertThat(n.get("txId").asLong(-1)).isGreaterThanOrEqualTo(0);
                txId2.set(n.get("txId").asLong());
              });

      // now, check events from event bus against tx ids obtained from notifications
      assertThat(events.signals())
          .extracting(PgListener.Signal::name, PgListener.Signal::ns, PgListener.Signal::type)
          .containsExactly(
              tuple(CHANNEL_FACT_INSERT, "test", "listenerTest"),
              tuple(CHANNEL_FACT_INSERT, "test", "listenerTest"));

      assertThat(events.signals())
          .extracting(PgListener.Signal::txId)
          .anySatisfy(txId -> assertThat(Long.parseLong(txId)).isEqualTo(txId1.get()))
          .anySatisfy(txId -> assertThat(Long.parseLong(txId)).isEqualTo(txId2.get()));
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
