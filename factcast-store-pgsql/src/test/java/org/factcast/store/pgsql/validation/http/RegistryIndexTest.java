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
package org.factcast.store.pgsql.validation.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RegistryIndexTest {

    @Test
    void testDeserialization() throws Exception {
        String json = "{\n" +
                "  \"schemes\": [\n" +
                "    {\n" +
                "      \"id\": \"namespaceA/eventA/1/schema.json\",\n" +
                "      \"type\": \"eventA\",\n" +
                "      \"version\": 1,\n" +
                "      \"hash\": \"f3daea5add7a553f69cac61543732745\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"id\": \"namespaceA/eventA/2/schema.json\",\n" +
                "      \"type\": \"eventA\",\n" +
                "      \"version\": 2,\n" +
                "      \"hash\": \"3e8d2f03b841e0cdaecd114f0d7162f8\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        RegistryIndex index = new ObjectMapper().readValue(json, RegistryIndex.class);

        assertEquals("f3daea5add7a553f69cac61543732745", index.schemes().get(0).hash());
    }
}
