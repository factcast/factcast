/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry.classpath;

import static org.junit.jupiter.api.Assertions.*;

import org.factcast.store.registry.transformation.TransformationSource;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.junit.jupiter.api.*;
import org.springframework.util.StringUtils;

class ClasspathRegistryFileFetcherTest {

  @Test
  void fetchTransformation() {
    var uut = new ClasspathRegistryFetcher("/example-registry");
    String transformation =
        uut.fetchTransformation(new TransformationSource("1-2", "xxx", "ns", "type", 1, 2));
    assertEquals(
        StringUtils.trimAllWhitespace(
            "function transform(event) {\n" + "    event.salutation = \"NA\"\n" + "}"),
        StringUtils.trimAllWhitespace(transformation));
  }

  @Test
  void fetchSchema() {
    var uut = new ClasspathRegistryFetcher("/example-registry");
    var s = uut.fetchSchema(new SchemaSource("x", "xxx", "ns", "type", 1));
    assertEquals(
        StringUtils.trimAllWhitespace(
            "{\n"
                + "  \"additionalProperties\" : true,\n"
                + "  \"properties\" : {\n"
                + "    \"firstName\" : {\n"
                + "      \"type\": \"string\"\n"
                + "    },\n"
                + "    \"lastName\" : {\n"
                + "      \"type\": \"string\"\n"
                + "    }\n"
                + "  },\n"
                + "  \"required\": [\"firstName\", \"lastName\"]\n"
                + "}\n"),
        StringUtils.trimAllWhitespace(s));
  }
}
