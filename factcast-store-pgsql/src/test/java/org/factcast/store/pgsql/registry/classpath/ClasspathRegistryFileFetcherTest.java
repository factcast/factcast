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
package org.factcast.store.pgsql.registry.classpath;

import static org.junit.jupiter.api.Assertions.*;

import lombok.val;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.junit.jupiter.api.*;

class ClasspathRegistryFileFetcherTest {

  @Test
  void fetchTransformation() {
    val uut = new ClasspathRegistryFileFetcher("/example-registry");
    String transformation =
        uut.fetchTransformation(new TransformationSource("1-2", "xxx", "ns", "type", 1, 2));
    assertEquals(
        "function transform(event) {\n" + "    event.salutation = \"NA\"\n" + "}", transformation);
  }

  @Test
  void fetchSchema() {
    val uut = new ClasspathRegistryFileFetcher("/example-registry");
    val s = uut.fetchSchema(new SchemaSource("x", "xxx", "ns", "type", 1));
    assertEquals(
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
            + "}\n",
        s);
  }
}
