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
package org.factcast.factus.serializer.fury;

import java.util.*;
import lombok.*;
import org.apache.fury.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class ComplexExampleTest {

  final String serializedWithFury010 =
      "Av9ZBEClKUTJc1A6JtFAKYgSnooBTpLUiRQBaMkjotI4FgT0TmPWS/SXAx6yAA50XKlB6us/eAAMAAHyCQAAuo5MABBuYXJmAF4ZGQsF/CxAlc0hQbNzrABCAQAANEVV9Ghvs4ilM5An+lc8uABGAQAAL0LyleDEBTjvxnyuBKxImgBEAQQBBxwE9E5j1kv0lwMesnOyoECuQbwbx1S4cwEqeSkvRA6jAPhAZCCcN3typya2rD+6dL8=";
  final String serializedWithJackson =
      "{\"b\":true,\"s\":12,\"i\":623517,\"d\":0.872345763,\"l\":1273,\"c\":\"x\",\"txt\":\"narf\",\"list\":[{\"uuid\":\"88b36f68-f455-4534-b83c-57fa279033a5\"}],\"set\":[{\"uuid\":\"3805c4e0-95f2-422f-9a48-ac04ae7cc6ef\"}],\"map\":{\"73b854c7-1bbc-41ae-a30e-442f29792a01\":{\"uuid\":\"727b379c-2064-40f8-bf74-ba3facb626a7\"}},\"bd\":0.7235481762346872364823468}";

  @SneakyThrows
  @Test
  void deserAndCompareToJson() {
    ThreadSafeFury fury = Fury.builder().requireClassRegistration(false).buildThreadSafeFury();

    ComplexExample exampleFromFury =
        (ComplexExample) fury.deserialize(Base64.getDecoder().decode(serializedWithFury010));
    String furyAsJson =
        new ObjectMapper().writerFor(ComplexExample.class).writeValueAsString(exampleFromFury);

    Assertions.assertThat(furyAsJson).isEqualTo(serializedWithJackson);
  }
}
