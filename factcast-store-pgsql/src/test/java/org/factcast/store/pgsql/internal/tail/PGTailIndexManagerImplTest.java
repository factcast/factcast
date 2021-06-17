package org.factcast.store.pgsql.internal.tail;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.UUID;
import lombok.val;
import org.assertj.core.util.Lists;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.internal.PgConstants;
import org.factcast.store.pgsql.internal.tail.PGTailIndexManagerImpl.HighWaterMark;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
class PGTailIndexManagerImplTest {
  @Mock private JdbcTemplate jdbc;
  @Mock private PgConfigurationProperties props;
  @Mock private PGTailIndexManagerImpl.HighWaterMark target;
  @InjectMocks private PGTailIndexManagerImpl underTest;

  @Nested
  class WhenTriggeringTailCreation {
    @BeforeEach
    void setup() {}

    @Test
    void returnsIfTailCreationIsDisabled() {
      val uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(false);

      uut.triggerTailCreation();

      verifyNoInteractions(jdbc);
      verify(uut, never()).refreshHighwaterMark();
    }

    @Test
    void createsTailIfIndexesEmpty() {
      val uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(jdbc.queryForList(anyString(), eq(String.class))).thenReturn(new LinkedList<>());
      doNothing().when(uut).createNewTail();

      uut.triggerTailCreation();

      verify(uut).createNewTail();
    }

    @Test
    void createsTailIfYoungestIndexTooOld() {
      val uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(jdbc.queryForList(anyString(), eq(String.class)))
          .thenReturn(Lists.newArrayList(PgConstants.TAIL_INDEX_NAME_PREFIX + "0"));
      doNothing().when(uut).createNewTail();

      uut.triggerTailCreation();

      verify(uut).createNewTail();
    }

    @Test
    void createsNoTailIfYoungestIndexIsRecent() {
      val uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      when(jdbc.queryForList(anyString(), eq(String.class)))
          .thenReturn(
              Lists.newArrayList(
                  PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 10000)));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail();
    }

    @Test
    void removesStaleIndexes() {
      val uut = spy(underTest);
      when(props.isTailIndexingEnabled()).thenReturn(true);
      when(props.getMinimumTailAge()).thenReturn(Duration.ofDays(1));
      when(props.getTailGenerationsToKeep()).thenReturn(2);
      String t1 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 10000);
      String t2 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 11000);
      String t3 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 12000);
      String t4 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 13000);
      String t5 = PgConstants.TAIL_INDEX_NAME_PREFIX + (System.currentTimeMillis() - 14000);

      when(jdbc.queryForList(anyString(), eq(String.class)))
          .thenReturn(Lists.newArrayList(t1, t2, t3, t4, t5));

      uut.triggerTailCreation();

      verify(uut, never()).createNewTail();
      verify(uut, times(3)).removeIndex(anyString());
      verify(uut).removeIndex(t3);
      verify(uut).removeIndex(t4);
      verify(uut).removeIndex(t5);
      verify(uut).refreshHighwaterMark();
    }
  }

  @Nested
  class WhenRemovingIndex {
    private final String INDEX_NAME = "INDEX_NAME";

    @BeforeEach
    void setup() {}

    @Test
    void dropsIndex() {

      val uut = spy(underTest);
      uut.removeIndex(INDEX_NAME);

      verify(jdbc).update("drop index INDEX_NAME");
    }
  }

  @Nested
  class WhenTimingToCreateANewTail {
    private final String STRING = "STRING";

    @BeforeEach
    void setup() {}

    @Test
    void parsesIndexTimestamp() {
      val uut = spy(underTest);
      when(props.getMinimumTailAge())
          .thenReturn(Duration.ofDays(1), Duration.ofHours(1), Duration.ofMinutes(1));

      val ts = System.currentTimeMillis() - 1000 * 60 * 30; // half hour before

      ArrayList<String> indexes = Lists.newArrayList(PgConstants.TAIL_INDEX_NAME_PREFIX + ts);
      val ret1 = uut.timeToCreateANewTail(indexes);
      val ret2 = uut.timeToCreateANewTail(indexes);
      val ret3 = uut.timeToCreateANewTail(indexes);

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

      val uut = spy(underTest);
      when(jdbc.queryForObject(anyString(), eq(Long.class))).thenReturn(118L);
      uut.createNewTail();

      long ts = System.currentTimeMillis() / 10000;
      verify(jdbc)
          .update(
              startsWith("create index concurrently " + PgConstants.TAIL_INDEX_NAME_PREFIX + ts));
      verify(jdbc).update(endsWith("WHERE ser>118"));
    }
  }

  @Nested
  class WhenRefreshingHighwaterMark {
    @Mock ResultSet rs;

    @BeforeEach
    void setup() {}

    @Test
    void exposesValues() {
      UUID id = UUID.randomUUID();
      long ser = 42L;

      val uut = spy(underTest);
      when(jdbc.queryForObject(anyString(), any(RowMapper.class)))
          .thenReturn(new HighWaterMark().targetId(id).targetSer(ser));

      uut.refreshHighwaterMark();

      assertThat(uut.targetId()).isEqualTo(id);
      assertThat(uut.targetSer()).isEqualTo(ser);
    }
  }
}
