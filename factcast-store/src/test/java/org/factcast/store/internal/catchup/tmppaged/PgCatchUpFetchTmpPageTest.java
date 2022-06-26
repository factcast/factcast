package org.factcast.store.internal.catchup.tmppaged;

import java.sql.PreparedStatement;
import java.util.concurrent.atomic.*;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.NonNull;
import lombok.SneakyThrows;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgCatchUpFetchTmpPageTest {

  private static final int PAGE_SIZE = 85;
  @Mock private @NonNull JdbcTemplate jdbc;
  @Mock private @NonNull SubscriptionRequestTO req;
  @Mock private @NonNull CurrentStatementHolder statementHolder;

  @Nested
  class WhenFetchingFacts {
    @Mock private @NonNull AtomicLong serial;
    PgCatchUpFetchTmpPage underTest;

    @BeforeEach
    void setup() {
      underTest = new PgCatchUpFetchTmpPage(jdbc, PAGE_SIZE, req, statementHolder);
    }

    @SneakyThrows
    @Test
    void setsStatementToHolder() {
      ArgumentCaptor<PreparedStatementSetter> captor =
          ArgumentCaptor.forClass(PreparedStatementSetter.class);

      underTest.fetchFacts(serial);

      verify(jdbc).query(Mockito.anyString(), captor.capture(), Mockito.any(PgFactExtractor.class));
      PreparedStatementSetter value = captor.getValue();
      verify(statementHolder).statement(null);

      PreparedStatement ps = mock(PreparedStatement.class);
      value.setValues(ps);

      verify(statementHolder).statement(same(ps));
      verifyNoMoreInteractions(statementHolder);
    }
  }
}
