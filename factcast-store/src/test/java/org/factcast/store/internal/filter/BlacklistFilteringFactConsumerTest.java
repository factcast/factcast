/*
 * Copyright © 2017-2024 factcast.org
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.function.Consumer;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.internal.filter.blacklist.Blacklist;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BlacklistFilteringFactConsumerTest {

  @Mock private Consumer<Fact> parent;
  @Mock private SubscriptionRequest request;
  private BlacklistFilteringFactConsumer underTest;
  @Mock public @NonNull Blacklist bl;

  @Nested
  class WhenAccepting {
    @Mock private Fact fact;

    @BeforeEach
    void setup() {
      underTest = new BlacklistFilteringFactConsumer(parent, request, bl);
    }

    @Test
    void passesNull() {
      underTest.accept(null);
      verify(parent).accept(null);
      verifyNoMoreInteractions(bl);
    }

    @Test
    void filters() {
      when(bl.isBlocked(any())).thenReturn(true);
      underTest.accept(fact);
      verifyNoInteractions(parent);
    }

    @Test
    void passesAlong() {
      when(bl.isBlocked(any())).thenReturn(false);
      underTest.accept(fact);
      verify(parent).accept(fact);
    }
  }
}
