package org.factcast.store.internal.catchup.tmppaged;

import java.sql.PreparedStatement;
import java.util.concurrent.atomic.*;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import lombok.SneakyThrows;

import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgCatchUpPrepareTest {
  @Mock private JdbcTemplate jdbc;
  @Mock private SubscriptionRequestTO req;
  @Mock private CurrentStatementHolder statementHolder;
  @InjectMocks private PgCatchUpPrepare underTest;

  @Nested
  class WhenPreparingCatchup {
    @Mock private AtomicLong serial;
    @Captor ArgumentCaptor<PreparedStatementCallback<Long>> captor;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void setsStatementToHolder() {
      when(jdbc.execute(Mockito.anyString(), captor.capture())).thenReturn(1L);

      underTest.prepareCatchup(serial);

      PreparedStatementCallback<Long> value = captor.getValue();
      verify(statementHolder).statement(null);

      PreparedStatement ps = mock(PreparedStatement.class);
      value.doInPreparedStatement(ps);

      verify(statementHolder).statement(same(ps));
      verifyNoMoreInteractions(statementHolder);
    }
  }
}
