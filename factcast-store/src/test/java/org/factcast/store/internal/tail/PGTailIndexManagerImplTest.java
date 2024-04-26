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
import org.assertj.core.api.Assertions;
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
  @Mock private PGTailIndexManagerImpl.CloseableJdbcTemplate jdbc;
  @Mock private PgConnectionSupplier pgConnectionSupplier;
  @Mock private StoreConfigurationProperties props;

  @Mock(strictness = Mock.Strictness.LENIENT)
  private PgMetrics pgMetrics;

  @InjectMocks private PGTailIndexManagerImpl underTest;

  @Nested
  class WhenTriggeringTailCreation {
    @Mock DistributionSummary distributionSummary;

    @BeforeEach
    void setup() {
      when(pgMetrics.distributionSummary(any(), any(Tags.class))).thenReturn(distributionSummary);
    }

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
      when(props.isTailIndexingEnabled()).thenReturn(true);
      doReturn(jdbc).when(uut).buildTemplate();
      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION)).thenReturn(new LinkedList<>());
      doNothing().when(uut).createNewTail(jdbc);

      uut.triggerTailCreation();

      verify(uut).createNewTail(jdbc);
      verify(pgMetrics)
          .distributionSummary(StoreMetrics.VALUE.TAIL_INDICES, Tags.of(Tag.of("state", "valid")));
      verify(pgMetrics)
          .distributionSummary(
              StoreMetrics.VALUE.TAIL_INDICES, Tags.of(Tag.of("state", "invalid")));
      verify(distributionSummary, times(2)).record(0);
    }

    @Test
    void createsTailIfYoungestIndexTooOld() {
      var uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      doReturn(jdbc).when(uut).buildTemplate();

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(
              Lists.newArrayList(
                  map(
                      INDEX_NAME_COLUMN,
                      PgConstants.TAIL_INDEX_NAME_PREFIX + "0",
                      VALID_COLUMN,
                      IS_VALID)));
      doNothing().when(uut).createNewTail(jdbc);

      uut.triggerTailCreation();

      verify(uut).createNewTail(jdbc);
      verify(distributionSummary).record(1);
      verify(distributionSummary).record(0);
    }

    @Test
    void createsNoTailIfYoungestIndexIsRecent_issue2571() {
      var uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      doReturn(jdbc).when(uut).buildTemplate();
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      when(props.getTailGenerationsToKeep()).thenReturn(3);

      final String t1 =
          PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 43200000); // 12 hours
      final String t2 =
          PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 172800000); // 2 days
      final String t3 =
          PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 259200000); // 3 days
      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(
              Lists.newArrayList(
                  map(INDEX_NAME_COLUMN, t1, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t2, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t3, VALID_COLUMN, IS_VALID)));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail(jdbc);
    }

    @Test
    void removesStaleIndexes() {
      var uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      doReturn(jdbc).when(uut).buildTemplate();
      when(props.getTailGenerationsToKeep()).thenReturn(2);
      String t1 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 10000);
      String t2 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 11000);
      String t3 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 12000);
      String t4 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 13000);
      String t5 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 14000);

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(
              Lists.newArrayList(
                  map(INDEX_NAME_COLUMN, t1, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t2, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t3, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t4, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t5, VALID_COLUMN, IS_VALID)));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail(jdbc);
      verify(uut, times(3)).removeIndex(eq(jdbc), anyString());
      verify(uut).removeIndex(jdbc, t3);
      verify(uut).removeIndex(jdbc, t4);
      verify(uut).removeIndex(jdbc, t5);
    }

    @Test
    void removesStaleInvalidIndexes() {
      var uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      doReturn(jdbc).when(uut).buildTemplate();
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      when(props.getTailGenerationsToKeep()).thenReturn(2);
      when(props.getTailCreationTimeout()).thenReturn(Duration.ofSeconds(5));

      long now = System.currentTimeMillis();
      String t1Valid = PgConstants.TAIL_INDEX_NAME_PREFIX + (now - 10000);
      String t2Valid = PgConstants.TAIL_INDEX_NAME_PREFIX + (now - 11000);
      String t3InvalidButRecent = PgConstants.TAIL_INDEX_NAME_PREFIX + (now - 60);

      // we remove invalid indices older than 2 hours from now, so use a timestamp
      // older than that
      var threeHours = Duration.ofHours(3).toMillis();
      String t4Invalid = PgConstants.TAIL_INDEX_NAME_PREFIX + (now - threeHours);

      var fourHours = Duration.ofHours(4).toMillis();
      String t5Invalid = PgConstants.TAIL_INDEX_NAME_PREFIX + (now - fourHours);

      when(jdbc.queryForList(LIST_FACT_INDEXES_WITH_VALIDATION))
          .thenReturn(
              Lists.newArrayList(
                  map(INDEX_NAME_COLUMN, t1Valid, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t2Valid, VALID_COLUMN, IS_VALID),
                  map(INDEX_NAME_COLUMN, t3InvalidButRecent, VALID_COLUMN, IS_INVALID),
                  map(INDEX_NAME_COLUMN, t4Invalid, VALID_COLUMN, IS_INVALID),
                  map(INDEX_NAME_COLUMN, t5Invalid, VALID_COLUMN, IS_INVALID)));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail(jdbc);
      // must not have removed valid recent indices, and also not invalid recent ones
      // (t3)
      verify(uut).removeIndex(jdbc, t4Invalid);
      verify(uut).removeIndex(jdbc, t5Invalid);
      verify(uut, times(2)).removeIndex(eq(jdbc), anyString());
      verify(distributionSummary, times(2)).record(2);
    }
  }

  @Nested
  class WhenRemovingIndex {
    private final String INDEX_NAME = "INDEX_NAME";

    @BeforeEach
    void setup() {}

    @Test
    void dropsIndex() {

      var uut = spy(underTest);
      uut.removeIndex(jdbc, INDEX_NAME);

      verify(jdbc).execute(anyString());
      verify(jdbc).update("DROP INDEX CONCURRENTLY IF EXISTS INDEX_NAME");
    }

    @Test
    void doesNotThrow() {
      var logCaptor = LogCaptor.forClass(PGTailIndexManagerImpl.class);
      when(jdbc.update(anyString()))
          .thenThrow(new DataAccessResourceFailureException("Some exception!"));

      var uut = spy(underTest);
      uut.removeIndex(jdbc, INDEX_NAME);

      assertThat(logCaptor.getErrorLogs()).contains("Error dropping tail index INDEX_NAME.");

      verify(jdbc).execute(anyString());
      verify(jdbc).update("DROP INDEX CONCURRENTLY IF EXISTS INDEX_NAME");
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

      List<String> input = new ArrayList<String>(List.of("5", "4", "3", "2", "1"));
      uut.removeOldestValidIndices(jdbc, input);

      Assertions.assertThat(input).hasSize(3).containsExactly("5", "4", "3");
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

      ArrayList<String> indexes = Lists.newArrayList(PgConstants.TAIL_INDEX_NAME_PREFIX + ts);
      var ret1 = uut.timeToCreateANewTail(indexes);
      var ret2 = uut.timeToCreateANewTail(indexes);
      var ret3 = uut.timeToCreateANewTail(indexes);

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
      when(pgConnectionSupplier.get()).thenReturn(pgConnection);

      var closeableJdbcTemplate = uut.buildTemplate();
      closeableJdbcTemplate.close();

      assertThat(closeableJdbcTemplate).isNotNull();

      verify(pgConnectionSupplier).get();
      verify(pgConnection).close();
    }
  }

  private Map<String, Object> map(String... keyValuePairs) {
    if (keyValuePairs == null) {
      return null;
    }

    if ((keyValuePairs.length % 2) != 0) {
      throw new IllegalArgumentException("Uneven list of key value pairs received, aborting...");
    }

    Map<String, Object> resultMap = new HashMap<>();
    for (int i = 0; i < (keyValuePairs.length / 2); i++) {
      resultMap.put(keyValuePairs[i * 2], keyValuePairs[(i * 2) + 1]);
    }
    return resultMap;
  }
}
