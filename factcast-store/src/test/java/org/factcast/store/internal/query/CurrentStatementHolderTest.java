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

import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import lombok.SneakyThrows;
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
      underTest.close();
    }

    @SneakyThrows
    @Test
    void cancelsStatement() {
      when(statement.getConnection()).thenReturn(connection);

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
  }
}
