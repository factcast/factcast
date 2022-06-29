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
package org.factcast.store.registry.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.everit.json.schema.Schema;
import org.everit.json.schema.loader.SpecificationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ValidationConstantsTest {

  @InjectMocks private ValidationConstants underTest;

  @Nested
  class WhenJsoningString2SchemaV7 {
    private final String SCHEMA_JSON = "SCHEMA_JSON";

    @BeforeEach
    void setup() {}

    @Test
    void createsWorkingSchema() {
      Schema schema = ValidationConstants.jsonString2SchemaV7("{}");
      assertThat(schema).isNotNull();
      // does not throw:
      schema.validate("{}");
    }

    @Test
    void isV7() {
      assertThat(ValidationConstants.getLoaderBuilder())
          .hasFieldOrPropertyWithValue("specVersion", SpecificationVersion.DRAFT_7);
    }
  }
}
