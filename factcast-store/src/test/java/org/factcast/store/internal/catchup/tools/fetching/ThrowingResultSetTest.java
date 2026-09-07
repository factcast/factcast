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
package org.factcast.store.internal.catchup.tools.fetching;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ThrowingResultSetTest {

  @Test
  void testNextThrowsException() {
    SQLException expectedException = new SQLException("DB error");
    ThrowingResultSet underTest = new ThrowingResultSet(expectedException);

    SQLException actualException = assertThrows(SQLException.class, underTest::next);
    assertEquals(expectedException, actualException);
  }

  @Test
  void testExceptionWrapping() {
    RuntimeException cause = new RuntimeException("wrapped error");
    ThrowingResultSet underTest = new ThrowingResultSet(cause);

    assertThrows(SQLException.class, underTest::next);
    try {
      underTest.next();
    } catch (SQLException e) {
      assertEquals(cause, e.getCause());
    }
  }

  @Test
  void testSqlExceptionWrapping() {
    SQLException cause = new SQLException("already sql");
    RuntimeException wrapper = new RuntimeException(cause);
    ThrowingResultSet underTest = new ThrowingResultSet(wrapper);

    assertThrows(SQLException.class, underTest::next);
    try {
      underTest.next();
    } catch (SQLException e) {
      assertEquals(cause, e);
    }
  }
}
