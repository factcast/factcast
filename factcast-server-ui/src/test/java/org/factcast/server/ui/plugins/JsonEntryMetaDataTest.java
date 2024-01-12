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

import java.util.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonEntryMetaDataTest {
  @Mock private Map<String, Collection<String>> annotations;
  @Mock private Map<String, Collection<String>> hoverContent;
  @InjectMocks private JsonEntryMetaData underTest;

  @Nested
  class WhenAnnotatingHeader {
    private final String PATH = "PATH";
    private final String VALUE = "VALUE";

    @Test
    void delegates() {
      underTest.annotateHeader("foo", "bar");
      underTest.annotateHeader("foo", "baz");
      Assertions.assertThat(underTest.annotations()).hasSize(1);
      Assertions.assertThat(underTest.annotations().get("header.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }

  @Nested
  class WhenAnnotatingPayload {
    private final String PATH = "PATH";
    private final String VALUE = "VALUE";

    @Test
    void delegates() {
      underTest.annotatePayload("foo", "bar");
      underTest.annotatePayload("foo", "baz");
      Assertions.assertThat(underTest.annotations()).hasSize(1);
      Assertions.assertThat(underTest.annotations().get("payload.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }

  @Nested
  class WhenAddingHeaderHoverContent {
    private final String PATH = "PATH";
    private final String VALUE = "VALUE";

    @Test
    void delegates() {
      underTest.addHeaderHoverContent("foo", "bar");
      underTest.addHeaderHoverContent("foo", "baz");
      Assertions.assertThat(underTest.hoverContent()).hasSize(1);
      Assertions.assertThat(underTest.hoverContent().get("header.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }

  @Nested
  class WhenAddingPayloadHoverContent {
    private final String PATH = "PATH";
    private final String VALUE = "VALUE";

    @Test
    void delegates() {
      underTest.addPayloadHoverContent("foo", "bar");
      underTest.addPayloadHoverContent("foo", "baz");
      Assertions.assertThat(underTest.hoverContent()).hasSize(1);
      Assertions.assertThat(underTest.hoverContent().get("payload.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }
}
