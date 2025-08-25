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
package org.factcast.store.internal.tail;

import static org.assertj.core.api.Assertions.*;
import static org.factcast.store.internal.PgConstants.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.time.Duration;
import java.util.*;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.assertj.core.util.Lists;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.factcast.store.internal.listen.PgConnectionSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class PGTailIndexManagerImplTest {
  @Mock private PGTailIndexManagerImpl.ClosingJdbcTemplate jdbc;
  @Mock private PgConnectionSupplier pgConnectionSupplier;
  @Mock private StoreConfigurationProperties props;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private PgMetrics pgMetrics;

  @InjectMocks private PGTailIndexManagerImpl underTest;

  @Nested
  class WhenTriggeringTailCreation {
    @Test
    void returnsIfTailCreationIsDisabled() {
      var uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(false);

      uut.triggerTailCreation();

      verify(uut, never()).buildTemplate();
      verifyNoInteractions(pgMetrics);
    }

    @Test
    void createsTailIfIndexesEmpty() {
      var uut = spy(underTest);
      doReturn(jdbc).when(uut).buildTemplate();
      doNothing().when(uut).createNewTail(jdbc);
      doReturn(false).when(uut).isAnyIndexOperationInProgress(jdbc);
      doNothing().when(uut).reportMetrics(jdbc, true);

      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION)).thenReturn(new LinkedList<>());

      uut.triggerTailCreation();

      verify(uut).createNewTail(jdbc);
      verify(uut, never()).removeTailIndex(any(), anyString());
      verify(uut).reportMetrics(jdbc, true);
    }

    @Test
    void createsTailIfYoungestIndexTooOld() {
      var uut = spy(underTest);
      doReturn(jdbc).when(uut).buildTemplate();
      doReturn(false).when(uut).isAnyIndexOperationInProgress(jdbc);
      doNothing().when(uut).reportMetrics(jdbc, true);
      doNothing().when(uut).createNewTail(jdbc);

      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(Lists.newArrayList(valid(PgConstants.TAIL_INDEX_NAME_PREFIX + "0")));

      uut.triggerTailCreation();

      verify(uut).createNewTail(jdbc);
      verify(uut).reportMetrics(jdbc, true);
    }

    @Test
    void createsNoTailIfYoungestIndexIsRecent_issue2571() {
      var uut = spy(underTest);
      doReturn(jdbc).when(uut).buildTemplate();
      doReturn(false).when(uut).isAnyIndexOperationInProgress(jdbc);
      doNothing().when(uut).reportMetrics(jdbc, true);

      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      when(props.getTailGenerationsToKeep()).thenReturn(3);

      final String t1 =
          PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 43200000); // 12 hours
      final String t2 =
          PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 172800000); // 2 days
      final String t3 =
          PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 259200000); // 3 days
      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(Lists.newArrayList(valid(t1), valid(t2), valid(t3)));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail(jdbc);
    }

    @Test
    void removesStaleIndexes() {
      var uut = spy(underTest);
      doReturn(jdbc).when(uut).buildTemplate();
      doReturn(false).when(uut).isAnyIndexOperationInProgress(jdbc);
      doNothing().when(uut).reportMetrics(jdbc, true);

      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      when(props.getTailGenerationsToKeep()).thenReturn(2);

      String t1 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 10000);
      String t2 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 11000);
      String t3 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 12000);
      String t4 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 13000);
      String t5 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 14000);

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(Lists.newArrayList(valid(t1), valid(t2), valid(t3), valid(t4), valid(t5)));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail(jdbc);
      verify(uut, times(3)).removeTailIndex(eq(jdbc), anyString());
      verify(uut).removeTailIndex(jdbc, t3);
      verify(uut).removeTailIndex(jdbc, t4);
      verify(uut).removeTailIndex(jdbc, t5);
    }

    @Test
    void noMaintenanceWhenIndexOperationInProgress() {
      var uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      doReturn(jdbc).when(uut).buildTemplate();
      doReturn(true).when(uut).isAnyIndexOperationInProgress(jdbc);
      doNothing().when(uut).reportMetrics(jdbc, false);

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail(jdbc);
      verify(uut, never()).removeTailIndex(any(), anyString());
      verify(uut).reportMetrics(jdbc, false);
    }

    @Test
    void removesInvalidIndexes() {
      var uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      when(props.getTailGenerationsToKeep()).thenReturn(2);
      doReturn(jdbc).when(uut).buildTemplate();
      doReturn(false).when(uut).isAnyIndexOperationInProgress(jdbc);
      doNothing().when(uut).reportMetrics(jdbc, true);

      long now = System.currentTimeMillis();
      String t1Valid = PgConstants.TAIL_INDEX_NAME_PREFIX + (now - 10000);
      String t2ValidAndRecent = PgConstants.TAIL_INDEX_NAME_PREFIX + (now - 60);
      String t3Invalid = PgConstants.TAIL_INDEX_NAME_PREFIX + 42;
      String t4Invalid = PgConstants.TAIL_INDEX_NAME_PREFIX + 43;

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(
              Lists.newArrayList(
                  valid(t1Valid), valid(t2ValidAndRecent), invalid(t3Invalid), invalid(t4Invalid)));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail(jdbc);
      verify(uut).removeTailIndex(jdbc, t3Invalid);
      verify(uut).removeTailIndex(jdbc, t4Invalid);
      verify(uut, times(2)).removeTailIndex(eq(jdbc), anyString());
    }
  }

  @Nested
  class WhenRemovingIndex {
    private final String INDEX_NAME = PgConstants.TAIL_INDEX_NAME_PREFIX + "42";

    @BeforeEach
    void setup() {}

    @Test
    void dropsIndex() {

      var uut = spy(underTest);
      uut.removeTailIndex(jdbc, INDEX_NAME);

      verify(jdbc).execute("set statement_timeout to 3600000");
      verify(jdbc).update("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX_NAME);
    }

    @Test
    void doesNotThrow() {
      var logCaptor = LogCaptor.forClass(PGTailIndexManagerImpl.class);
      when(jdbc.update(anyString()))
          .thenThrow(new DataAccessResourceFailureException("Some exception!"));

      var uut = spy(underTest);
      uut.removeTailIndex(jdbc, INDEX_NAME);

      assertThat(logCaptor.getErrorLogs())
          .contains("Error dropping tail index " + INDEX_NAME + ".");

      verify(jdbc).execute("set statement_timeout to 3600000");
      verify(jdbc).update("DROP INDEX CONCURRENTLY IF EXISTS " + INDEX_NAME);
    }
  }

  @Nested
  class WhenRemovingOldestIndex {
    private final String INDEX_NAME = "INDEX_NAME";

    @BeforeEach
    void setup() {

      when(props.getTailGenerationsToKeep()).thenReturn(3);
    }

    @Test
    void removeOldestValidIndicies() {
      var uut = spy(underTest);

      List<Map<String, Object>> input =
          new ArrayList<>(
              List.of(
                  valid(PgConstants.TAIL_INDEX_NAME_PREFIX + "5"),
                  valid(PgConstants.TAIL_INDEX_NAME_PREFIX + "4"),
                  valid(PgConstants.TAIL_INDEX_NAME_PREFIX + "3"),
                  valid(PgConstants.TAIL_INDEX_NAME_PREFIX + "2"),
                  valid(PgConstants.TAIL_INDEX_NAME_PREFIX + "1")));

      uut.removeOldestValidIndices(jdbc, input);

      verify(uut).removeTailIndex(jdbc, PgConstants.TAIL_INDEX_NAME_PREFIX + "2");
      verify(uut).removeTailIndex(jdbc, PgConstants.TAIL_INDEX_NAME_PREFIX + "1");
    }
  }

  @Nested
  class WhenTimingToCreateANewTail {

    @BeforeEach
    void setup() {}

    @Test
    void parsesIndexTimestamp() {
      var uut = spy(underTest);
      when(props.getMinimumTailAge())
          .thenReturn(Duration.ofDays(1), Duration.ofHours(1), Duration.ofMinutes(1));

      var ts = System.currentTimeMillis() - (1000 * 60 * 30); // half hour before

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(List.of(valid(PgConstants.TAIL_INDEX_NAME_PREFIX + ts)));

      var ret1 = uut.timeToCreateANewTail(jdbc);
      var ret2 = uut.timeToCreateANewTail(jdbc);
      var ret3 = uut.timeToCreateANewTail(jdbc);

      assertThat(ret1).isFalse();
      assertThat(ret2).isFalse();
      assertThat(ret3).isTrue();
    }
  }

  @Nested
  class WhenCreatingNewTail {
    @BeforeEach
    void setup() {}

    @Test
    void createsIndex() {

      var uut = spy(underTest);
      when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(118L);
      uut.createNewTail(jdbc);

      long ts = System.currentTimeMillis() / 10000;
      verify(jdbc)
          .update(
              startsWith("create index concurrently " + PgConstants.TAIL_INDEX_NAME_PREFIX + ts));
      verify(jdbc).update(endsWith("WHERE ser>118"));
    }

    @Test
    void dropsIndexUponException() {

      var uut = spy(underTest);
      when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(118L);
      long ts = System.currentTimeMillis() / 10000;
      when(jdbc.update(startsWith("create index concurrently " + tailIndexName(ts))))
          .thenThrow(new RuntimeException("Some exception!"));

      // RUN
      uut.createNewTail(jdbc);

      verify(jdbc).update(startsWith("create index concurrently " + tailIndexName(ts)));
      verify(jdbc).update(startsWith(dropTailIndex(tailIndexName(ts))));
      verify(jdbc).update(endsWith("WHERE ser>118"));
    }

    @Test
    void dropsIndexUponException_withAnotherException() {

      var uut = spy(underTest);
      when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(118L);
      long ts = System.currentTimeMillis() / 10000;
      when(jdbc.update(startsWith("create index concurrently " + tailIndexName(ts))))
          .thenThrow(new RuntimeException("Some exception!"));
      when(jdbc.update(startsWith(dropTailIndex(tailIndexName(ts)))))
          .thenThrow(new RuntimeException("Another exception!"));

      // RUN
      uut.createNewTail(jdbc);

      verify(jdbc).update(startsWith("create index concurrently " + tailIndexName(ts)));
      verify(jdbc).update(startsWith(dropTailIndex(tailIndexName(ts))));
      // this must still happen:
      verify(jdbc).update(endsWith("WHERE ser>118"));
    }
  }

  @Nested
  class WhenBuildingTemplate {
    @Mock PgConnection pgConnection;

    @Test
    @SneakyThrows
    void createsTemplate() {
      var uut = spy(underTest);
      when(pgConnectionSupplier.getUnpooledConnection(any())).thenReturn(pgConnection);

      var closeableJdbcTemplate = uut.buildTemplate();
      closeableJdbcTemplate.close();

      assertThat(closeableJdbcTemplate).isNotNull();

      verify(pgConnectionSupplier).getUnpooledConnection("tail-index-maintenance");
      verify(pgConnection).close();
    }
  }

  @Nested
  class WhenReportingMetrics {
    @Mock DistributionSummary distributionSummary;

    @BeforeEach
    void setup() {
      when(pgMetrics.distributionSummary(any(), any(Tags.class))).thenReturn(distributionSummary);
    }

    @Test
    void reportsMetrics_maintenancePossible() {
      List<Map<String, Object>> indices =
          new ArrayList<>(List.of(valid("5"), valid("4"), invalid("3")));

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION)).thenReturn(indices);

      underTest.reportMetrics(jdbc, true);

      verify(pgMetrics)
          .distributionSummary(
              StoreMetrics.VALUE.TAIL_INDICES,
              Tags.of(Tag.of("state", "valid"), Tag.of("maintenance", "executed")));
      verify(pgMetrics)
          .distributionSummary(
              StoreMetrics.VALUE.TAIL_INDICES,
              Tags.of(Tag.of("state", "invalid"), Tag.of("maintenance", "executed")));
      verify(distributionSummary).record(2.0);
      verify(distributionSummary).record(1.0);
    }

    @Test
    void reportsMetrics_maintenanceNotPossible() {
      List<Map<String, Object>> indices =
          new ArrayList<>(List.of(valid("5"), valid("4"), invalid("3")));

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION)).thenReturn(indices);

      underTest.reportMetrics(jdbc, false);

      verify(pgMetrics)
          .distributionSummary(
              StoreMetrics.VALUE.TAIL_INDICES,
              Tags.of(Tag.of("state", "valid"), Tag.of("maintenance", "skipped")));
      verify(pgMetrics)
          .distributionSummary(
              StoreMetrics.VALUE.TAIL_INDICES,
              Tags.of(Tag.of("state", "invalid"), Tag.of("maintenance", "skipped")));
      verify(distributionSummary).record(2.0);
      verify(distributionSummary).record(1.0);
    }
  }

  @Nested
  class WhenAnyIndexOperationInProgress {
    @Test
    void noIndexOperations() {
      when(jdbc.queryForList(INDEX_OPERATIONS_IN_PROGRESS)).thenReturn(List.of());

      assertThat(underTest.isAnyIndexOperationInProgress(jdbc)).isFalse();
    }

    @Test
    void reportsMetrics_maintenanceNotPossible() {
      when(jdbc.queryForList(INDEX_OPERATIONS_IN_PROGRESS))
          .thenReturn(List.of(Map.of("foo", "bar")));

      assertThat(underTest.isAnyIndexOperationInProgress(jdbc)).isTrue();
    }
  }

  private static Map<String, Object> valid(String name) {
    return Map.of(INDEX_NAME_COLUMN, name, VALID_COLUMN, IS_VALID);
  }

  private static Map<String, Object> invalid(String name) {
    return Map.of(INDEX_NAME_COLUMN, name, VALID_COLUMN, IS_INVALID);
  }
}
