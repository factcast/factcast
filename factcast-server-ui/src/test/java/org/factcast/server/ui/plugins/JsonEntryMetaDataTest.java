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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
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

    @Test
    void delegates() {
      underTest.annotateHeader("foo", "bar");
      underTest.annotateHeader("foo", "baz");
      assertThat(underTest.annotations()).hasSize(1);
      assertThat(underTest.annotations().get("header.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }

  @Nested
  class WhenAnnotatingPayload {

    @Test
    void delegates() {
      underTest.annotatePayload("foo", "bar");
      underTest.annotatePayload("foo", "baz");
      assertThat(underTest.annotations()).hasSize(1);
      assertThat(underTest.annotations().get("payload.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }

  @Nested
  class WhenAddingHeaderHoverContent {

    @Test
    void delegates() {
      underTest.addHeaderHoverContent("foo", "bar");
      underTest.addHeaderHoverContent("foo", "baz");
      assertThat(underTest.hoverContent()).hasSize(1);
      assertThat(underTest.hoverContent().get("header.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }

  @Nested
  class WhenAddingPayloadHoverContent {

    @Test
    void delegates() {
      underTest.addPayloadHoverContent("foo", "bar");
      underTest.addPayloadHoverContent("foo", "baz");
      assertThat(underTest.hoverContent()).hasSize(1);
      assertThat(underTest.hoverContent().get("payload.foo"))
          .hasSize(2)
          .containsExactlyInAnyOrder("bar", "baz");
    }
  }

  @Nested
  class WhenAddingHeaderMetaFilterOption {

    @Test
    void delegates() {
      underTest.addHeaderMetaFilterOption("path", "foo", List.of("bar"));

      assertThat(underTest.filterOptions()).hasSize(1);
      assertThat(underTest.filterOptions().get("header.path"))
          .extracting(JsonEntryMetaData.FilterOptions::meta)
          .extracting(
              JsonEntryMetaData.MultiMetaFilterOption::key,
              JsonEntryMetaData.MultiMetaFilterOption::value)
          .containsExactlyInAnyOrder("foo", List.of("bar"));
    }
  }

  @Nested
  class WhenAddingPayloadAggregateIdFilterOption {

    @Test
    void delegates() {
      final var aggregateId = UUID.randomUUID();
      underTest.addPayloadAggregateIdFilterOption("path", aggregateId);

      assertThat(underTest.filterOptions()).hasSize(1);
      assertThat(underTest.filterOptions().get("payload.path"))
          .extracting(JsonEntryMetaData.FilterOptions::aggregateId)
          .isEqualTo(aggregateId);
    }
  }
}
