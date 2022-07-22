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
package org.factcast.store.internal.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.*;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.PostQueryMatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactFilterImplTest {

  private static final boolean SKIP_TEST = true;
  @Mock private SubscriptionRequestTO request;
  @Mock private PgBlacklist blacklist;
  @Mock private PostQueryMatcher matcher;

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
      var underTest = new FactFilterImpl(request, blacklist, matcher);

      assertThat(underTest.test(fact)).isFalse();
      verify(matcher, never()).test(any());
    }

    @Test
    void filtersNonMatch() {
      UUID id = UUID.randomUUID();
      when(fact.id()).thenReturn(id);
      when(blacklist.isBlocked(id)).thenReturn(false);
      when(matcher.test(any())).thenReturn(false);
      when(matcher.canBeSkipped()).thenReturn(false);
      var underTest = new FactFilterImpl(request, blacklist, matcher);

      assertThat(underTest.test(fact)).isFalse();
    }

    @Test
    void happyPath() {
      UUID id = UUID.randomUUID();
      when(fact.id()).thenReturn(id);
      when(blacklist.isBlocked(id)).thenReturn(false);
      when(matcher.canBeSkipped()).thenReturn(false);
      when(matcher.test(fact)).thenReturn(true);

      var underTest = new FactFilterImpl(request, blacklist, matcher);
      assertThat(underTest.test(fact)).isTrue();
    }
  }
}
