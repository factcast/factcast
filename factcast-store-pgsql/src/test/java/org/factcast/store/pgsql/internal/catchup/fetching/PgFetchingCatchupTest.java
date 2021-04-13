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
package org.factcast.store.pgsql.internal.catchup.fetching;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.concurrent.atomic.AtomicLong;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.TransformationException;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgPostQueryMatcher;
import org.factcast.store.pgsql.internal.listen.PgConnectionSupplier;
import org.factcast.store.pgsql.internal.rowmapper.PgFactExtractor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

@ExtendWith(MockitoExtension.class)
class PgFetchingCatchupTest {

  @Mock private @NonNull PgConnectionSupplier connectionSupplier;
  @Mock private @NonNull PgConfigurationProperties props;
  @Mock private @NonNull SubscriptionRequestTO req;
  @Mock private @NonNull PgPostQueryMatcher postQueryMatcher;
  @Mock private @NonNull SubscriptionImpl subscription;
  @Mock private @NonNull AtomicLong serial;
  @InjectMocks private PgFetchingCatchup underTest;

  @Nested
  class WhenRunning {
    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void connectionHandling() {
      PgConnection con = mock(PgConnection.class);
      when(connectionSupplier.get()).thenReturn(con);

      PgFetchingCatchup uut = spy(underTest);
      doNothing().when(uut).fetch(any());

      uut.run();

      verify(con).setAutoCommit(false);
      verify(con).close();
    }
  }

  @Nested
  class WhenFetching {
    @Mock private @NonNull JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
      Mockito.when(props.getPageSize()).thenReturn(47);
    }

    @Test
    void setsCorrectFetchSize() {
      doNothing()
          .when(jdbc)
          .query(anyString(), any(PreparedStatementSetter.class), any(RowCallbackHandler.class));
      underTest.fetch(jdbc);
      verify(jdbc).setFetchSize(eq(props.getPageSize()));
    }
  }

  @Nested
  class WhenCreatingRowCallbackHandler {
    private final boolean SKIP_TESTING = true;
    @Mock private PgFactExtractor extractor;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void skipsPostQueryMatching() {
      RowCallbackHandler cbh = underTest.createRowCallbackHandler(true, extractor);
      cbh.processRow(mock(ResultSet.class));

      verifyNoInteractions(postQueryMatcher);
    }

    @SneakyThrows
    @Test
    void filtersInPostQueryMatching() {
      RowCallbackHandler cbh = underTest.createRowCallbackHandler(false, extractor);
      ResultSet rs = mock(ResultSet.class);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(false);
      cbh.processRow(rs);

      verifyNoInteractions(subscription);
    }

    @SneakyThrows
    @Test
    void notifies() {
      RowCallbackHandler cbh = underTest.createRowCallbackHandler(false, extractor);
      ResultSet rs = mock(ResultSet.class);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(true);
      cbh.processRow(rs);

      verify(subscription).notifyElement(testFact);
    }

    @SneakyThrows
    @Test
    void notifiesTransformationException() {
      RowCallbackHandler cbh = underTest.createRowCallbackHandler(false, extractor);
      ResultSet rs = mock(ResultSet.class);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(true);
      doThrow(TransformationException.class).when(subscription).notifyElement(testFact);
      assertThatThrownBy(
          () -> {
            cbh.processRow(rs);
          });

      verify(subscription).notifyError(any());
    }

    @SneakyThrows
    @Test
    void closesOnRTException() {
      RowCallbackHandler cbh = underTest.createRowCallbackHandler(false, extractor);
      ResultSet rs = mock(ResultSet.class);
      Fact testFact = new TestFact();
      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
      when(postQueryMatcher.test(testFact)).thenReturn(true);
      doThrow(RuntimeException.class).when(subscription).notifyElement(testFact);

      assertThatThrownBy(
          () -> {
            cbh.processRow(rs);
          });

      verify(subscription).close();
    }
  }
}
