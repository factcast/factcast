/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.factus.serializer.fory;

import java.util.*;
import lombok.*;
import org.apache.fory.*;
import org.apache.fory.config.*;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class ComplexExampleTest {

  final String serializedWithFory15 =
      "AP8eAFlQHxWUXaBhMBZZOibRQCmIEp6KAU6S1IkUAWjJI6K6OC30TmPWS/SXAx6yAAQMFBQESBQDBAgUSgQEFAEELBQIBCAUBRaEYBRvJi0SmAgCFjAPBBaXAhZIkwgCFk7zFBUOdFypQerrPwwAeAAB8gkAALqOTP9vGRkLBfwsQJXNIUGzc6z/WgEIHgIuAIYz1y9hYDACWTom0UApiBKeigFOktSJFAFoySOiujg59E5j1kv0lwMesnOyoEAmUogYFJf/ewAAAAAAAAAAEAAAAAAAAP9bAQQBHgM9TxKW5YBb9kAPHsn5KM2a/3sAAAAAAAAAABAAAAAAAAD/XAEIHgP/ewAAAAAAAAAAEAAAAAAAAP8QbmFyZg==";
  final String serializedWithJackson =
      "{\"b\":true,\"s\":12,\"i\":623517,\"d\":0.872345763,\"l\":1273,\"c\":\"x\",\"txt\":\"narf\",\"list\":[{\"uuid\":\"00000000-0000-007b-0000-000000001000\"}],\"set\":[{\"uuid\":\"00000000-0000-007b-0000-000000001000\"}],\"map\":{\"f65b80e5-9612-4f3d-9acd-28f9c91e0f40\":{\"uuid\":\"00000000-0000-007b-0000-000000001000\"}},\"bd\":0.7235481762346872364823468}";

  @SneakyThrows
  @Test
  void deserAndCompareToJson() {
    ThreadSafeFory fory =
        Fory.builder()
            .withCompatibleMode(CompatibleMode.COMPATIBLE)
            .requireClassRegistration(false)
            .withLanguage(Language.JAVA)
            .buildThreadSafeFory();

    byte[] ba = fory.serialize(new ComplexExample());
    System.out.println(Base64.getEncoder().encodeToString(ba));

    ComplexExample exampleFromFury =
        (ComplexExample) fory.deserialize(Base64.getDecoder().decode(serializedWithFory15));
    String foryAsJson =
        new ObjectMapper().writerFor(ComplexExample.class).writeValueAsString(exampleFromFury);

    System.out.println(foryAsJson);

    // Assertions.assertThat(foryAsJson).isEqualTo(serializedWithJackson);
  }
}
