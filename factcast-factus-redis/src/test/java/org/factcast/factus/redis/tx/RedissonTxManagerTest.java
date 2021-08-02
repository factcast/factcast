package org.factcast.factus.redis.tx;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RTransaction;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class RedissonTxManagerTest {

  @Mock private AtomicReference<RTransaction> tx;
  @Mock private RedissonClient redisson;
  @InjectMocks private RedissonTxManager underTest;

  @Nested
  class WhenGetting {
    @Test
    void noCurrentIfUnstarted() {
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void currentIfStarted() {
      underTest.startOrJoin();
      assertThat(underTest.getCurrentTransaction()).isNull();
    }
  }

  @Nested
  class WhenCommiting {
    @Mock RTransaction tx;

    @Test
    void joins() {
      when(redisson.createTransaction(any())).thenReturn(tx);
      underTest.startOrJoin();

      underTest.commit();

      verify(tx).commit();
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void commit_exception() {
      when(redisson.createTransaction(any())).thenReturn(tx);
      doThrow(new IllegalStateException("foo")).when(tx).commit();
      underTest.startOrJoin();

      assertThatThrownBy(() -> underTest.commit()).isInstanceOf(IllegalStateException.class);

      verify(tx).commit();
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void skipsIfNoTxRunning() {
      RTransaction curr = underTest.getCurrentTransaction();
      assertThat(curr).isNull();
      underTest.commit();

      // no asserttion, does not throw exception, thats it
    }
  }

  @Nested
  class WhenRollingBack {
    @Mock RTransaction tx;

    @Test
    void joins() {
      when(redisson.createTransaction(any())).thenReturn(tx);
      underTest.startOrJoin();

      underTest.rollback();

      verify(tx).rollback();
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void rollback_exception() {
      when(redisson.createTransaction(any())).thenReturn(tx);
      doThrow(new IllegalStateException("foo")).when(tx).rollback();
      underTest.startOrJoin();

      assertThatThrownBy(() -> underTest.rollback()).isInstanceOf(IllegalStateException.class);

      verify(tx).rollback();
      assertThat(underTest.getCurrentTransaction()).isNull();
    }

    @Test
    void skipsIfNoTxRunning() {
      RTransaction curr = underTest.getCurrentTransaction();
      assertThat(curr).isNull();
      underTest.rollback();

      // no asserttion, does not throw exception, thats it
    }
  }

  @Nested
  class WhenJoining {
    @Mock private Consumer<RTransaction> block;

    @BeforeEach
    void setup() {
      when(redisson.createTransaction(any())).thenAnswer(i -> mock(RTransaction.class));
    }

    @Test
    void startsIfNecessary() {
      underTest.join(
          tx -> {
            assertThat(tx).isNotNull();
          });
    }

    @Test
    void keepsCurrent() {
      underTest.startOrJoin();
      final var curr = underTest.getCurrentTransaction();
      underTest.join(
          tx -> {
            assertThat(tx).isSameAs(curr);
          });
    }
  }

  @Nested
  class WhenJoiningFn {
    @Mock private Function<RTransaction, ?> block;

    @BeforeEach
    void setup() {
      when(redisson.createTransaction(any())).thenAnswer(i -> mock(RTransaction.class));
    }

    @Test
    void startsIfNecessary() {
      underTest.join(
          tx -> {
            assertThat(tx).isNotNull();
            return 0;
          });
    }

    @Test
    void keepsCurrent() {
      underTest.startOrJoin();
      final var curr = underTest.getCurrentTransaction();
      underTest.join(
          tx -> {
            assertThat(tx).isSameAs(curr);
            return 0;
          });
    }
  }

  @Nested
  class WhenStartingOrJoin {
    private RedissonTxManager uut;
    @Mock private RTransaction rtx;

    @BeforeEach
    void setup() {

      uut = underTest;
    }

    @Test
    void createsIfNull() {
      when(redisson.createTransaction(any())).thenReturn(rtx);

      assertThat(uut.getCurrentTransaction()).isNull();
      assertThat(uut.startOrJoin()).isEqualTo(true);
      assertThat(uut.getCurrentTransaction()).isSameAs(rtx);
    }

    @Test
    void returnsIfNotNull() {
      when(redisson.createTransaction(any())).thenReturn(rtx, (RTransaction) null);
      // first time, it should create
      assertThat(uut.getCurrentTransaction()).isNull();
      assertThat(uut.startOrJoin()).isEqualTo(true);
      assertThat(uut.getCurrentTransaction()).isSameAs(rtx);
      verify(redisson).createTransaction(any());

      assertThat(uut.startOrJoin()).isEqualTo(false);
      assertThat(uut.getCurrentTransaction()).isSameAs(rtx);
      assertThat(uut.startOrJoin()).isEqualTo(false);
      assertThat(uut.getCurrentTransaction()).isSameAs(rtx);

      verifyNoMoreInteractions(redisson);
    }
  }
}
