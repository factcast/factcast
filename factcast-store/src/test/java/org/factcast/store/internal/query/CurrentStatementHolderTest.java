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
      underTest.destroy();
    }

    @SneakyThrows
    @Test
    void cancelsStatement() {
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(false);

      underTest.register(statement);

      underTest.destroy();
      verify(statement).cancel();
      verify(connection).rollback();
    }

    @SneakyThrows
    @Test
    void cancelsStatementAndCatchesException() {
      underTest.register(statement);
      doThrow(SQLException.class).when(statement).cancel();
      underTest.destroy();
      verify(statement).cancel();
    }

    @SneakyThrows
    @Test
    void skipsIfWasCanceled() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      underTest.register(statement);
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(false);
      underTest.destroy();

      underTest.destroy();

      verify(statement, atMostOnce()).cancel();
      verify(connection, atMostOnce()).rollback();
      // no longer complains
      assertThat(logCaptor.getTraceLogs()).isEmpty();
    }

    @SneakyThrows
    @Test
    void skipsIfStatementIsNull() {
      LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
      underTest.destroy();

      assertThat(logCaptor.getTraceLogs()).isEmpty();
    }
  }

  @Nested
  class AdditionalCoverageTests {

    @Test
    void testGettersAndStateFlags() {
      assertThat(underTest.wasCanceled()).isFalse();
      assertThat(underTest.wasDestroyed()).isFalse();

      underTest.destroy();
      assertThat(underTest.wasDestroyed()).isTrue();
    }

    @Test
    void testTrackConnection() {
      Connection tracked = underTest.track(connection);
      assertThat(tracked).isNotNull().isInstanceOf(StatementTrackingConnection.class);
    }

    @SneakyThrows
    @Test
    void testRegisterReplacement() {
      Statement statement2 = mock(Statement.class);
      doThrow(SQLException.class).when(statement).cancel();

      underTest.register(statement);
      underTest.register(statement2);

      verify(statement).cancel();
      assertThat(underTest.statement()).isEqualTo(statement2);
    }

    @SneakyThrows
    @Test
    void testUnregisterWarnings() {
      try (LogCaptor logCaptor = LogCaptor.forClass(CurrentStatementHolder.class)) {
        // Unregister when null (unnecessary clear)
        underTest.unregister(statement);
        assertThat(logCaptor.getWarnLogs()).anyMatch(log -> log.contains("Unnecessary clear"));

        // Statement confusion (unregistering wrong statement)
        underTest.register(statement);
        Statement other = mock(Statement.class);
        underTest.unregister(other);
        assertThat(logCaptor.getWarnLogs()).anyMatch(log -> log.contains("Statement confusion"));
      }
    }

    @SneakyThrows
    @Test
    void testCancelWhenStatementIsNull() {
      try (LogCaptor logCaptor = LogCaptor.forClass(CurrentStatementHolder.class)) {
        underTest.cancel();
        assertThat(logCaptor.getTraceLogs()).anyMatch(log -> log.contains("Statement not set"));
      }
    }

    @SneakyThrows
    @Test
    void testCancelWithAutoCommitTrue() {
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(true);

      underTest.register(statement);
      underTest.cancel();

      verify(connection, never()).rollback();
      verify(connection).close();
      verify(statement).cancel();
      verify(statement).close();
      assertThat(underTest.wasCanceled()).isTrue();
    }

    @SneakyThrows
    @Test
    void testTryRollbackSQLException() {
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenThrow(SQLException.class);

      underTest.register(statement);
      underTest.cancel();

      verify(connection, never()).rollback();
      verify(connection).close();
    }

    @SneakyThrows
    @Test
    void testRollbackThrowsSQLException() {
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(false);
      doThrow(SQLException.class).when(connection).rollback();

      underTest.register(statement);
      underTest.cancel();

      verify(connection).rollback();
      verify(connection).close();
    }

    @SneakyThrows
    @Test
    void testTryCloseSQLException() {
      when(statement.getConnection()).thenReturn(connection);
      when(connection.getAutoCommit()).thenReturn(true);
      doThrow(SQLException.class).when(connection).close();

      underTest.register(statement);
      underTest.cancel();

      verify(connection).close();
    }

    @SneakyThrows
    @Test
    void testCancelStatementSQLException() {
      when(statement.getConnection()).thenReturn(connection);
      doThrow(SQLException.class).when(statement).cancel();

      underTest.register(statement);
      underTest.cancel();

      verify(statement).cancel();
    }

    @SneakyThrows
    @Test
    void testGetConnectionFromSQLException() {
      when(statement.getConnection()).thenThrow(SQLException.class);

      underTest.register(statement);
      underTest.cancel();

      verify(statement).cancel();
    }

    @Test
    void testCheckStateDestroyedThrowsIllegalStateException() {
      underTest.destroy();

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.register(statement))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already closed");

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.unregister(statement))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already closed");

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.cancel())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already closed");

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.track(connection))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already closed");
    }

    @SneakyThrows
    @Test
    void testCheckStateCanceledThrowsIllegalStateException() {
      underTest.register(statement);
      underTest.cancel();

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.register(statement))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already canceled");

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.unregister(statement))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already canceled");

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.cancel())
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already canceled");

      org.assertj.core.api.Assertions.assertThatThrownBy(() -> underTest.track(connection))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already canceled");
    }
  }
}
