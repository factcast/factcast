/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import lombok.NonNull;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("unchecked")
@ExtendWith(MockitoExtension.class)
class SpecBuilderTest {

  @Spy private SpecBuilder underTest = mock(SpecBuilder.class);

  @Nested
  class WhenFollowing {
    @Mock private @NonNull FactSpec specification;

    @BeforeEach
    void setup() {
      when(underTest.follow(specification)).thenCallRealMethod();
    }

    @Test
    void wrapsSingleInstanceInList() {
      ArgumentCaptor<Collection<FactSpec>> captor = ArgumentCaptor.forClass(Collection.class);
      underTest.follow(specification);
      verify(underTest).follow(captor.capture());
      assertThat(captor.getValue()).hasSize(1).containsExactly(specification);
    }
  }

  @Nested
  class WhenCatchingUp {
    @Mock private @NonNull FactSpec specification;

    @BeforeEach
    void setup() {
      when(underTest.catchup(specification)).thenCallRealMethod();
    }

    @Test
    void wrapsSingleInstanceInList() {
      ArgumentCaptor<Collection<FactSpec>> captor = ArgumentCaptor.forClass(Collection.class);
      underTest.catchup(specification);
      verify(underTest).catchup(captor.capture());
      assertThat(captor.getValue()).hasSize(1).containsExactly(specification);
    }
  }
}
