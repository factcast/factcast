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
package org.factcast.server.ui.plugins;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import lombok.Setter;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonViewPluginTest {
  private JsonViewPlugin underTest = new Foo();

  @Nested
  class WhenHandling {
    @Mock private Fact fact;
    @Mock private JsonPayload payload;
    @Mock private JsonEntryMetaData jsonEntryMetaData;

    @Test
    void delegatesWhenReady() {
      underTest = spy(new Foo().ready(true));

      underTest.handle(fact, payload, jsonEntryMetaData);

      verify(underTest, times(1)).handle(fact, payload, jsonEntryMetaData);
    }

    @Test
    void skipsWhenNotReady() {
      underTest = spy(new Foo());

      underTest.handle(fact, payload, jsonEntryMetaData);

      verify(underTest, never()).doHandle(any(), any(), any());
    }
  }

  @Nested
  class WhenGettingDisplayName {
    @Test
    void defaultName() {
      Assertions.assertThat(new Foo().getDisplayName()).isNotNull().isEqualTo("Foo");
    }
  }

  class Foo extends JsonViewPlugin {
    @Setter boolean ready = false;

    @Override
    protected boolean isReady() {
      return ready;
    }

    @Override
    protected void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {}
  }
}
