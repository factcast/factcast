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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class ComplexExampleTest {

  final String serializedWithFory15 = "AP8dLAT/qGTxmHV3OibRQCmIEp6KAU6S1IkUAWjJI6K6OBYE9E5j1kv0lwMesgAOdFypQerrP3gADAAB8hO6jkz/EG5hcmb/WgEM/3sAAAAAAAAAABAAAAAAAAD/XAEM/3sAAAAAAAAAABAAAAAAAAD/ZQEEAR0DHAT0TmPWS/SXAx6yc7KgQPZHK3MtfHgf1+LimhMzjLb/ewAAAAAAAAAAEAAAAAAAAP9sGRkLBfwsQJXNIUGzc6w=";
  final String serializedWithJackson =
      "{\"b\":true,\"s\":12,\"i\":623517,\"d\":0.872345763,\"l\":1273,\"c\":\"x\",\"txt\":\"narf\",\"list\":[{\"uuid\":\"00000000-0000-007b-0000-000000001000\"}],\"set\":[{\"uuid\":\"00000000-0000-007b-0000-000000001000\"}],\"map\":{\"1f787c2d-732b-47f6-b68c-33139ae2e2d7\":{\"uuid\":\"00000000-0000-007b-0000-000000001000\"}},\"bd\":0.7235481762346872364823468}";

  @SneakyThrows
  @Test
  void deserAndCompareToJson() {
    ThreadSafeFory fory = Fory.builder().requireClassRegistration(false).buildThreadSafeFory();

    ComplexExample exampleFromFury =
        (ComplexExample) fory.deserialize(Base64.getDecoder().decode(serializedWithFory15));
    String foryAsJson =
        new ObjectMapper().writerFor(ComplexExample.class).writeValueAsString(exampleFromFury);

    Assertions.assertThat(foryAsJson).isEqualTo(serializedWithJackson);
  }
}
