package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import org.factcast.factus.redis.tx.RedissonTxManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
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
  class WhenJoining {
    @Mock private Consumer<RTransaction> block;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenJoiningFn {
    @Mock private Function<RTransaction, ?> block;

    @BeforeEach
    void setup() {}
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
