/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ReadOnlyTokenStoreTest {

  @InjectMocks private ReadOnlyTokenStore underTest;

  @Nested
  class WhenCreating {
    @Mock private @NonNull State state;

    @Test
    void fails() {
      assertThatThrownBy(() -> underTest.create(state))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class WhenInvalidating {
    @Mock private @NonNull StateToken token;

    @Test
    void fails() {
      assertThatThrownBy(() -> underTest.invalidate(token))
          .isInstanceOf(UnsupportedOperationException.class);
    }
  }

  @Nested
  class WhenGetting {
    @Mock private @NonNull StateToken token;

    @Test
    void alwaysEmpty() {
      assertThat(underTest.get(token)).isEmpty();
    }
  }
}
