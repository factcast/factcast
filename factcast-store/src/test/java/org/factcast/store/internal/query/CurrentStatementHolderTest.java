/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.*;
import nl.altindag.log.LogCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.PreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
class CurrentStatementHolderTest {
  @Mock private Statement statement;

  @Mock private Connection connection;

  @Spy private CurrentStatementHolder underTest;

  @Nested
  class WhenClosing {
    @BeforeEach
    void setup() {}

    @Test
    void ignoresNull() {
      underTest.close();
    }

    @SneakyThrows
    @Test
    void cancelsStatement() {
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(false);

      underTest.statement(statement);

      underTest.close();
      verify(statement).cancel();
      verify(connection).rollback();
    }

    @SneakyThrows
    @Test
    void cancelsStatementAndCatchesException() {
      underTest.statement(statement);
      doThrow(SQLException.class).when(statement).cancel();
      underTest.close();
      verify(statement).cancel();
    }

    @SneakyThrows
    @Test
    void skipsIfWasCanceled() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      underTest.statement(statement);
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(false);
      underTest.close();

      underTest.close();

      verify(statement, atMostOnce()).cancel();
      verify(connection, atMostOnce()).rollback();
      // no longer complains
      assertThat(logCaptor.getTraceLogs()).isEmpty();
    }

    @SneakyThrows
    @Test
    void skipsIfStatementIsNull() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      underTest.clear();

      underTest.close();

      assertThat(logCaptor.getTraceLogs()).isEmpty();
    }
  }

  @Nested
  class AdditionalCoverageTests {
    @SneakyThrows
    @Test
    void contextPreparedStatementDelegatesCloseAndClears() {
      PreparedStatement ps = mock(PreparedStatement.class);
      PreparedStatement contextPs = underTest.register(ps);
      underTest.statement(statement);
      contextPs.close();
      verify(ps).close();
      assertThat(underTest.statement()).isNull();
    }

    @SneakyThrows
    @Test
    void contextPreparedStatementDelegatesCloseOnCompletionAndClears() {
      PreparedStatement ps = mock(PreparedStatement.class);
      PreparedStatement contextPs = underTest.register(ps);
      underTest.statement(statement);
      contextPs.closeOnCompletion();
      verify(ps).closeOnCompletion();
      assertThat(underTest.statement()).isNull();
    }

    @Test
    void doubleWrappingContextPreparedStatementLogsWarning() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      PreparedStatement ps = mock(PreparedStatement.class);
      PreparedStatement wrapped = underTest.register(ps);
      underTest.register(wrapped);
      assertThat(logCaptor.getWarnLogs())
          .contains("Double wrapping of ContextPreparedStatement prevented. This is a bug.");
    }

    @SneakyThrows
    @Test
    void prepareStatementWithSqlAndCon() {
      Connection con = mock(Connection.class);
      PreparedStatement ps = mock(PreparedStatement.class);
      when(con.prepareStatement("SELECT 1")).thenReturn(ps);

      PreparedStatement result = underTest.prepareStatement(con, "SELECT 1");
      assertThat(result).isNotNull();
      assertThat(underTest.statement()).isNotNull();
    }

    @SneakyThrows
    @Test
    void prepareStatementWithSetter() {
      Connection con = mock(Connection.class);
      PreparedStatement ps = mock(PreparedStatement.class);
      when(con.prepareStatement("SELECT 1")).thenReturn(ps);
      PreparedStatementSetter setter = mock(PreparedStatementSetter.class);

      PreparedStatement result = underTest.prepareStatement(con, "SELECT 1", setter);
      assertThat(result).isNotNull();
      verify(setter).setValues(any(PreparedStatement.class));
    }

    @Test
    void cancelWhenStatementIsNullLogsTrace() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      underTest.clear();
      underTest.cancel();
      assertThat(logCaptor.getTraceLogs())
          .contains("Statement not set, so no canceling necessary. This is a bug.");
    }

    @SneakyThrows
    @Test
    void tryRollbackWithNullConnectionDoesNothing() {
      underTest.tryRollback(null);
    }

    @SneakyThrows
    @Test
    void tryRollbackWhenAutoCommitIsTrue() {
      Connection c = mock(Connection.class);
      when(c.getAutoCommit()).thenReturn(true);
      underTest.tryRollback(c);
      verify(c, never()).rollback();
      verify(c).close();
    }

    @SneakyThrows
    @Test
    void tryRollbackCatchesSQLException() {
      Connection c = mock(Connection.class);
      when(c.getAutoCommit()).thenThrow(SQLException.class);
      underTest.tryRollback(c);
      verify(c).close();
    }

    @SneakyThrows
    @Test
    void tryCloseCatchesSQLException() {
      Connection c = mock(Connection.class);
      doThrow(SQLException.class).when(c).close();
      CurrentStatementHolder.tryClose(c);
    }

    @SneakyThrows
    @Test
    void cancelStatementCatchesSQLException() {
      Statement st = mock(Statement.class);
      doThrow(SQLException.class).when(st).cancel();
      underTest.cancelStatement(st);
      assertThat(underTest.wasCanceled()).isTrue();
    }

    @SneakyThrows
    @Test
    void getConnectionFromCatchesSQLException() {
      Statement st = mock(Statement.class);
      when(st.getConnection()).thenThrow(SQLException.class);
      Connection c = underTest.getConnectionFrom(st);
      assertThat(c).isNull();
    }

    @SneakyThrows
    @Test
    void overwritingRunningStatementLogsWarning() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      Statement st1 = mock(Statement.class);
      Statement st2 = mock(Statement.class);
      underTest.statement(st1);
      underTest.statement(st2);
      assertThat(logCaptor.getWarnLogs())
          .contains("Overwriting a running statement? This is a bug.");
    }

    @SneakyThrows
    @Test
    void statementAlreadyCanceledLogsWarningAndCompensates() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      Statement st = mock(Statement.class);
      when(st.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(true);
      underTest.statement(st);
      underTest.cancel();
      assertThat(underTest.wasCanceled()).isTrue();

      Statement st2 = mock(Statement.class);
      underTest.statement(st2);
      assertThat(logCaptor.getWarnLogs())
          .contains("Statement was already canceled, compensating. This is a bug.");
      assertThat(underTest.wasCanceled()).isFalse();
    }
  }
}
