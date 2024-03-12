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
package org.factcast.store.internal.catchup.fetching;

import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Disabled // TODO
class PgFetchingCatchupTest {
  //
  //  @Mock @NonNull PgConnectionSupplier connectionSupplier;
  //
  //  @Mock(lenient = true)
  //  @NonNull
  //  StoreConfigurationProperties props;
  //
  //  @Mock @NonNull SubscriptionRequestTO req;
  //  @Mock @NonNull FactFilter filter;
  //  @Mock @NonNull SubscriptionImpl subscription;
  //  @Mock @NonNull AtomicLong serial;
  //  @Mock @NonNull CurrentStatementHolder statementHolder;
  //  @Mock @NonNull BufferedTransformingFactPipeline interceptor;
  //
  //  @Mock(lenient = true)
  //  @NonNull
  //  PgMetrics metrics;
  //
  //  @Mock @NonNull Counter counter;
  //  @InjectMocks PgFetchingCatchup underTest;
  //
  //  @Nested
  //  class WhenRunning {
  //    @BeforeEach
  //    void setup() {}
  //
  //    @SneakyThrows
  //    @Test
  //    void connectionHandling() {
  //      PgConnection con = mock(PgConnection.class);
  //      when(connectionSupplier.get()).thenReturn(con);
  //
  //      var uut = spy(underTest);
  //      doNothing().when(uut).fetch(any());
  //
  //      uut.run();
  //
  //      verify(con).setAutoCommit(false);
  //      verify(con).close();
  //    }
  //
  //    @SneakyThrows
  //    @Test
  //    void removesCurrentStatement() {
  //      PgConnection con = mock(PgConnection.class);
  //      when(connectionSupplier.get()).thenReturn(con);
  //      var uut = spy(underTest);
  //      doNothing().when(uut).fetch(any());
  //
  //      uut.run();
  //
  //      verify(statementHolder).clear();
  //    }
  //  }
  //
  //  @Nested
  //  class WhenFetching {
  //    @Mock @NonNull JdbcTemplate jdbc;
  //
  //    @BeforeEach
  //    void setup() {
  //      Mockito.when(props.getPageSize()).thenReturn(47);
  //      when(metrics.counter(StoreMetrics.EVENT.CATCHUP_FACT)).thenReturn(counter);
  //    }
  //
  //    @Test
  //    void setsCorrectFetchSize() {
  //      doNothing()
  //          .when(jdbc)
  //          .query(anyString(), any(PreparedStatementSetter.class),
  // any(RowCallbackHandler.class));
  //      underTest.fetch(jdbc);
  //      verify(jdbc).setFetchSize(eq(props.getPageSize()));
  //    }
  //  }
  //
  //  @Nested
  //  class WhenCreatingRowCallbackHandler {
  //    final boolean SKIP_TESTING = true;
  //    @Mock PgFactExtractor extractor;
  //
  //    @BeforeEach
  //    void setup() {}
  //
  //    @SneakyThrows
  //    @Test
  //    void passesFact() {
  //      final var cbh = underTest.createRowCallbackHandler(extractor);
  //      ResultSet rs = mock(ResultSet.class);
  //      Fact testFact = new TestFact();
  //      when(extractor.mapRow(rs, 0)).thenReturn(testFact);
  //      cbh.processRow(rs);
  //
  //      verify(interceptor).accept(testFact);
  //    }
  //
  //    @SneakyThrows
  //    @Test
  //    void passesFactEscalatesException() {
  //      final var cbh = underTest.createRowCallbackHandler(extractor);
  //      ResultSet rs = mock(ResultSet.class);
  //      Fact testFact = new TestFact();
  //      when(extractor.mapRow(same(rs), anyInt())).thenReturn(testFact);
  //      doThrow(TransformationException.class).when(interceptor).accept(testFact);
  //
  //      assertThatThrownBy(
  //              () -> {
  //                cbh.processRow(rs);
  //              })
  //          .isInstanceOf(TransformationException.class);
  //    }
  //
  //    @Test
  //    @SneakyThrows
  //    void swallowsExceptionAfterCancel() {
  //      final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
  //      ResultSet rs = mock(ResultSet.class);
  //      when(statementHolder.wasCanceled()).thenReturn(false, true);
  //
  //      // until
  //      PSQLException mockException = mock(PSQLException.class);
  //      when(rs.getString(anyString())).thenThrow(mockException);
  //      // must not throw
  //      cbh.processRow(rs);
  //    }
  //
  //    @Test
  //    @SneakyThrows
  //    void returnsIfCancelled() {
  //      final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
  //      ResultSet rs = mock(ResultSet.class);
  //      when(statementHolder.wasCanceled()).thenReturn(true);
  //      // must not throw
  //      cbh.processRow(rs);
  //    }
  //
  //    @Test
  //    @SneakyThrows
  //    void throwsWhenNotCanceled() {
  //      final var cbh = underTest.createRowCallbackHandler(new PgFactExtractor(new AtomicLong()));
  //      ResultSet rs = mock(ResultSet.class);
  //      // it should appear open,
  //      when(rs.isClosed()).thenReturn(false);
  //      // until
  //      PSQLException mockException =
  //          mock(PSQLException.class, withSettings().strictness(Strictness.LENIENT));
  //      when(rs.getString(anyString())).thenThrow(mockException);
  //
  //      assertThatThrownBy(
  //              () -> {
  //                cbh.processRow(rs);
  //              })
  //          .isInstanceOf(SQLException.class);
  //    }
  //
  //    @Test
  //    @SneakyThrows
  //    void throwsWhenCanceledButUnexpectedException() {
  //      final var cbh = underTest.createRowCallbackHandler(extractor);
  //      ResultSet rs = mock(ResultSet.class);
  //      // it should appear open,
  //      when(rs.isClosed()).thenReturn(false);
  //      // until
  //      when(extractor.mapRow(any(), anyInt())).thenThrow(RuntimeException.class);
  //
  //      assertThatThrownBy(
  //              () -> {
  //                cbh.processRow(rs);
  //              })
  //          .isInstanceOf(RuntimeException.class);
  //    }
  //  }
}
