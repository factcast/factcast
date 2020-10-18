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
package org.factcast.store.pgsql.internal;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicLong;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

@ExtendWith(MockitoExtension.class)
public class PgSynchronizedQueryTest {

  PgSynchronizedQuery uut;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  JdbcTemplate jdbcTemplate;

  final String sql = "SELECT 42";

  @Mock PreparedStatementSetter setter;

  @Mock RowCallbackHandler rowHandler;

  @Mock AtomicLong serialToContinueFrom;

  @Mock PgLatestSerialFetcher fetcher;

  @Test
  public void testRunWithIndex() {
    uut =
        new PgSynchronizedQuery(
            jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom, fetcher);
    uut.run(true);
    verify(jdbcTemplate, never()).execute(startsWith("SET LOCAL enable_bitmapscan"));
  }

  @Test
  public void testRunWithoutIndex() {
    uut =
        new PgSynchronizedQuery(
            jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom, fetcher);
    uut.run(false);
    verify(jdbcTemplate).execute(startsWith("SET LOCAL enable_bitmapscan"));
  }
}
