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
package org.factcast.server.ui.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JsonUtilsTest {
  @InjectMocks private JsonUtils underTest = new JsonUtils(new ObjectMapper());

  @Nested
  class WhenForingString {
    private final String JSON = "{\"key\":\"value\"}";

    @Test
    void reEvaluateDocumentContextAfterModification() {
      final var jsonPayload = underTest.forString(JSON);

      assertThat(jsonPayload.findAnyPaths()).hasSize(1);

      jsonPayload.remove("$.key");

      assertThat(jsonPayload.findAnyPaths()).hasSize(0);
    }
  }
}
