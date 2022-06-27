package org.factcast.store.internal.filter;

import java.util.*;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.PgPostQueryMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PgFactFilterImplTest {

  private static final boolean SKIP_TEST = true;
  @Mock private SubscriptionRequestTO request;
  @Mock private PgBlacklist blacklist;
  @Mock private PgPostQueryMatcher matcher;
  @InjectMocks private PgFactFilterImpl underTest;

  @Nested
  class WhenTesting {
    @Mock private Fact fact;
    final UUID id = UUID.randomUUID();

    @BeforeEach
    void setup() {}

    @Test
    void filtersBlacklisted() {
      when(fact.id()).thenReturn(id);
      when(blacklist.isBlocked(id)).thenReturn(true);
      assertThat(underTest.test(fact)).isFalse();
      verify(matcher, never()).test(any());
    }

    @Test
    void filtersNonMatch() {
      UUID id = UUID.randomUUID();
      when(fact.id()).thenReturn(id);
      when(blacklist.isBlocked(id)).thenReturn(false);
      when(matcher.test(any())).thenReturn(false);
      assertThat(underTest.test(fact)).isFalse();
    }

    @Test
    void happyPath() {
      UUID id = UUID.randomUUID();
      when(fact.id()).thenReturn(id);
      when(blacklist.isBlocked(id)).thenReturn(false);
      when(matcher.test(any())).thenReturn(true);

      assertThat(underTest.test(fact)).isTrue();
    }
  }
}
