/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal.catchup;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.apache.tomcat.jdbc.pool.PooledConnection;
import org.factcast.store.internal.ConnectionModifier;
import org.factcast.store.internal.pipeline.PushbackServerPipeline;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;

@ExtendWith(MockitoExtension.class)
class CatchupDataSourceTest {

  @Mock(strictness = Mock.Strictness.LENIENT)
  Connection connection;

  @Mock PushbackServerPipeline pipeline;

  @Mock PgConnection pgConnection;

  @Mock PooledConnection pooledConnection;

  List<ConnectionModifier> modifiers = Collections.emptyList();

  CatchupDataSource underTest;

  @SneakyThrows
  @BeforeEach
  void setUp() {
    lenient().when(connection.unwrap(PgConnection.class)).thenReturn(pgConnection);
    lenient().when(connection.unwrap(PooledConnection.class)).thenReturn(pooledConnection);

    underTest = new CatchupDataSource(connection, modifiers, pipeline);
  }

  @Test
  void registersWithPipelineOnInstantiation() {
    verify(pipeline).register(underTest);
  }

  @Nested
  class WhenDestroying {

    @Test
    @SneakyThrows
    void destroysSuccessfullyWhenNotCanceled() {
      when(connection.getAutoCommit()).thenReturn(true);

      underTest.destroy();

      verify(pipeline).unregister(underTest);
      verify(connection).getAutoCommit();
      verify(connection, never()).rollback();
    }

    @Test
    @SneakyThrows
    void rollsBackTransactionOnDestroyIfAutoCommitFalse() {
      when(connection.getAutoCommit()).thenReturn(false);

      underTest.destroy();

      verify(pipeline).unregister(underTest);
      verify(connection).getAutoCommit();
      verify(connection).rollback();
    }

    @Test
    @SneakyThrows
    void discardsAndAbortsOnDestroyException() {
      when(connection.getAutoCommit()).thenThrow(new SQLException("fail"));

      underTest.destroy();

      verify(pipeline).unregister(underTest);
      verify(pooledConnection).setDiscarded(true);
      verify(pgConnection).abort(any());
    }

    @Test
    @SneakyThrows
    void destroysQuietlyWhenCanceled() {
      underTest.cancel();
      // reset interactions from cancel()
      clearInvocations(pipeline, connection, pgConnection, pooledConnection);

      underTest.destroy();

      verify(pipeline).unregister(underTest);
      verify(connection, never()).getAutoCommit();
      verify(connection, never()).rollback();
    }
  }

  @Nested
  class WhenCanceling {

    @Test
    @SneakyThrows
    void cancelsDiscardsAndAborts() {
      underTest.cancel();

      verify(pgConnection).cancelQuery();
      verify(pooledConnection).setDiscarded(true);
      verify(pgConnection).abort(any());
    }
  }

  @Nested
  class WhenHandlingUnwrapFailures {

    @Test
    @SneakyThrows
    void handlesNullOrExceptioningUnwrapGracefully() {
      Connection badConnection =
          mock(Connection.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      lenient().when(badConnection.unwrap(any())).thenReturn(null);
      lenient().when(badConnection.getAutoCommit()).thenThrow(new SQLException("fail"));

      try (LogCaptor logCaptor = LogCaptor.forClass(CatchupDataSource.class)) {
        CatchupDataSource badDs = new CatchupDataSource(badConnection, modifiers, pipeline);

        assertDoesNotThrow(() -> badDs.destroy());

        assertThat(logCaptor.getWarnLogs())
            .contains(
                "Unwrapping of PgConnection failed. This is ok, if we're in a unit test",
                "Unwrapping of PooledConnection failed. This is ok, if we're in a unit test",
                "Error preparing a connection for reuse");
      }
    }

    @Test
    @SneakyThrows
    void handlesUnwrapExceptionGracefully() {
      Connection badConnection =
          mock(Connection.class, withSettings().strictness(org.mockito.quality.Strictness.LENIENT));
      lenient().when(badConnection.unwrap(any())).thenThrow(new SQLException("unwrap fail"));

      try (LogCaptor logCaptor = LogCaptor.forClass(CatchupDataSource.class)) {
        CatchupDataSource badDs = new CatchupDataSource(badConnection, modifiers, pipeline);

        assertDoesNotThrow(() -> badDs.cancel());
        assertDoesNotThrow(() -> badDs.destroy());

        assertThat(logCaptor.getWarnLogs())
            .contains(
                "Cancelling of orphaned query failed on datasource destruction ",
                "Discarding of connection failed on datasource destruction ");
      }
    }
  }
}
