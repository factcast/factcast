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
package org.factcast.store.internal;

import java.sql.ResultSet;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.filter.FactFilter;
import org.factcast.store.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import lombok.SneakyThrows;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

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

  @Nested
  class FactRowCallbackHandlerTest {
    @Mock(lenient = true)
    private ResultSet rs;

    @Mock SubscriptionImpl subscription;

    @Mock Supplier<Boolean> isConnectedSupplier;

    @Mock AtomicLong serial;

    @Mock SubscriptionRequestTO request;
    @Mock FactFilter filter;
    @Mock FactInterceptor interceptor;

    @InjectMocks private PgSynchronizedQuery.FactRowCallbackHandler uut;

    @Test
    @SneakyThrows
    void test_notConnected() {
      when(isConnectedSupplier.get()).thenReturn(false);

      uut.processRow(rs);

      verifyNoInteractions(rs, filter, serial, request);
    }

    @Test
    @SneakyThrows
    void test_rsClosed() {
      when(isConnectedSupplier.get()).thenReturn(true);
      when(rs.isClosed()).thenReturn(true);

      assertThatThrownBy(() -> uut.processRow(rs)).isInstanceOf(IllegalStateException.class);

      verifyNoInteractions(filter, serial, request);
    }

    @Test
    @SneakyThrows
    void test_happyCase() {
      when(isConnectedSupplier.get()).thenReturn(true);

      when(rs.isClosed()).thenReturn(false);
      when(rs.getString(PgConstants.ALIAS_ID)).thenReturn("550e8400-e29b-11d4-a716-446655440000");
      when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("foo");
      when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(10L);

      uut.processRow(rs);

      verify(interceptor, times(1)).accept(any());
      verify(serial).set(10L);
    }

    @Test
    @SneakyThrows
    void test_exception() {
      when(isConnectedSupplier.get()).thenReturn(true);

      when(rs.isClosed()).thenReturn(false);
      when(rs.getString(PgConstants.ALIAS_ID)).thenReturn("550e8400-e29b-11d4-a716-446655440000");
      when(rs.getString(PgConstants.ALIAS_NS)).thenReturn("foo");
      when(rs.getString(PgConstants.COLUMN_HEADER)).thenReturn("{}");
      when(rs.getString(PgConstants.COLUMN_PAYLOAD)).thenReturn("{}");
      when(rs.getLong(PgConstants.COLUMN_SER)).thenReturn(10L);

      var exception = new IllegalArgumentException();
      doThrow(exception).when(interceptor).accept(any());

      uut.processRow(rs);

      verify(interceptor).accept(any());
      verify(subscription).notifyError(exception);
      verify(rs).close();
      verify(serial, never()).set(10L);
    }
  }
}
