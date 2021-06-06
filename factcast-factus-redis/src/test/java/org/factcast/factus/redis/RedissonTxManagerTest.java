package org.factcast.factus.redis;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
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
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenGettingOrCreate {
    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenJoining {
    @Mock private Consumer<RTransaction> block;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class _WhenJoining {
    @Mock private Function<RTransaction, ?> block;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenJoiningOrAutoCommit {
    @Mock private Consumer<RTransaction> block;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class _WhenJoiningOrAutoCommit {
    @Mock private Function<RTransaction, ?> block;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenJoiningOrAutoCommitAsync {
    @Mock private Consumer<RTransaction> block;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class _WhenJoiningOrAutoCommitAsync {
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

  @Nested
  class WhenCommitting {
    private final boolean ASYNC = true;

    @BeforeEach
    void setup() {}
  }

  @Nested
  class WhenRollbacking {
    @BeforeEach
    void setup() {}
  }
}
