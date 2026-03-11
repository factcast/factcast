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

import com.google.common.collect.*;
import java.util.*;
import lombok.SneakyThrows;
import org.apache.fury.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

class ComplexExampleTest {

  final String serializedWithFury09 =
      "Av9ZBEClKUTJc1A6JtFAKYgSnooBTpLUiRQBaMkjotI4FgT0TmPWS/SXAx6yAA50XKlB6us/eAAMAAHyCQAAuo5MABBuYXJmAF4ZGQsF/CxAlc0hQbNzrABCAQAAIU2gVobMH1ViK0Lc2ydLlwBGAQAAK0Nv7s4eE/DNeBdtAefurQBEAQB3T9jbViQWu1a/aj1Cg0SGAAccBPROY9ZL9JcDHrJzsqBAADJC0FgIJP/nI2neocJh7ak=";
  final String serializedWithJackson =
      "{\"b\":true,\"s\":12,\"i\":623517,\"d\":0.872345763,\"l\":1273,\"c\":\"x\",\"txt\":\"narf\",\"list\":[{\"uuid\":\"551fcc86-56a0-4d21-974b-27dbdc422b62\"}],\"set\":[{\"uuid\":\"f0131ece-ee6f-432b-adee-e7016d1778cd\"}],\"map\":{\"bb162456-dbd8-4f77-8644-83423d6abf56\":{\"uuid\":\"e7ff2408-58d0-4232-a9ed-61c2a1de6923\"}},\"bd\":0.7235481762346872364823468}";

  @SneakyThrows
  @Test
  void deserAndCompareToJson() {
    ThreadSafeFury fury = Fury.builder().requireClassRegistration(false).buildThreadSafeFury();

    ComplexExample exampleFromFury =
        (ComplexExample) fury.deserialize(Base64.getDecoder().decode(serializedWithFury09));
    String furyAsJson =
        new ObjectMapper().writerFor(ComplexExample.class).writeValueAsString(exampleFromFury);

    Assertions.assertThat(furyAsJson).isEqualTo(serializedWithJackson);
  }
}
