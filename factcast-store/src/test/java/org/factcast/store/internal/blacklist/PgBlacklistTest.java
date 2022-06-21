package org.factcast.store.internal.blacklist;

import java.util.*;

import org.factcast.store.internal.listen.PgListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;

import lombok.SneakyThrows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgBlacklistTest {
  private static final UUID UUID_VALUE = UUID.randomUUID();
  @Spy private EventBus bus = new EventBus();
  @Mock private PgBlacklist.Fetcher fetcher;
  @InjectMocks private PgBlacklist underTest;

  @Nested
  class WhenAfteringSingletonsInstantiated {
    @Test
    void fetches() {
      underTest.afterSingletonsInstantiated();
      verify(fetcher).get();
    }

    @Test
    void registersOnBus() {
      underTest.afterSingletonsInstantiated();
      verify(fetcher, times(1)).get();
      // three more
      bus.post(new PgListener.BlacklistChangeSignal());
      bus.post(new PgListener.BlacklistChangeSignal());
      bus.post(new PgListener.BlacklistChangeSignal());
      verify(fetcher, times(4)).get();
    }
  }

  @Nested
  class WhenDisposing {
    @SneakyThrows
    @Test
    void unregisters() {
      underTest.afterSingletonsInstantiated();
      underTest.destroy();
      verify(bus).unregister(underTest);
    }
  }

  @Nested
  class WhenCheckingIfIsBlocked {
    private final UUID FACT_ID = UUID.randomUUID();
    private final UUID NEW_FACT_ID = UUID.randomUUID();

    @BeforeEach
    void setup() {
      //noinspection unchecked
      when(fetcher.get())
          .thenReturn(
              Sets.newHashSet(FACT_ID),
              Sets.newHashSet(NEW_FACT_ID, FACT_ID),
              Collections.emptySet());
      underTest.afterSingletonsInstantiated();
    }

    @Test
    void remembersBlocked() {
      assertThat(underTest.isBlocked(FACT_ID)).isTrue();
      assertThat(underTest.isBlocked(UUID.randomUUID())).isFalse();
    }

    @Test
    void updatesSet() {
      assertThat(underTest.isBlocked(FACT_ID)).isTrue();
      assertThat(underTest.isBlocked(NEW_FACT_ID)).isFalse();

      underTest.on(new PgListener.BlacklistChangeSignal());
      assertThat(underTest.isBlocked(FACT_ID)).isTrue();
      assertThat(underTest.isBlocked(NEW_FACT_ID)).isTrue();

      underTest.on(new PgListener.BlacklistChangeSignal());
      assertThat(underTest.isBlocked(FACT_ID)).isFalse();
      assertThat(underTest.isBlocked(NEW_FACT_ID)).isFalse();
    }
  }

  @Nested
  class WhenOning {
    @Mock private PgListener.BlacklistChangeSignal signal;

    @Test
    void fetches() {
      underTest.on(signal);
      verify(fetcher).get();
    }
  }
}
