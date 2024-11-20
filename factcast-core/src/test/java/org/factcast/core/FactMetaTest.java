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
package org.factcast.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.SneakyThrows;
import org.assertj.core.api.*;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactMetaTest {

  @Nested
  class WhenDeserializing {

    @SneakyThrows
    @Test
    void matchesExcpectation() {
      String json =
          "{\"someString\":\"oink\",\"meta\":{\"single\":\"value\",\"someNull\":null,\"otherNull\":[null,null],\"foo\":[\"bar\",\"baz\"]}}";
      TestMeta deser = new ObjectMapper().readerFor(TestMeta.class).readValue(json);
      Assertions.assertThat(deser.someString).isEqualTo("oink");
      Assertions.assertThat(deser.meta).hasSize(6);
      Assertions.assertThat(deser.meta.keySet()).hasSize(4); // unique keys
      Assertions.assertThat(deser.meta.get("foo")).isEqualTo("bar");
      Assertions.assertThat(deser.meta.get("single")).isEqualTo("value");
      Assertions.assertThat(deser.meta.getAll("foo"))
          .hasSize(2)
          .containsAll(Lists.newArrayList("bar", "baz"));
    }
  }

  @Nested
  class WhenSeserializing {

    @SneakyThrows
    @Test
    void matchesExcpectation() {
      String json =
          "{\"someString\":\"oink\",\"meta\":{\"single\":\"value\",\"someNull\":null,\"otherNull\":[null,null],\"foo\":[\"bar\",\"baz\"]}}";
      String ser = new ObjectMapper().writeValueAsString(new ExampleMeta());
      Assertions.assertThat(ser).isEqualTo(json);
    }
  }

  @Nested
  class WhenIterating {
    @Test
    void forEachProcessesAllTuples() {
      AtomicInteger count = new AtomicInteger();
      new ExampleMeta()
          .meta.forEach(
              (k, v) -> {
                count.incrementAndGet();
              });
      Assertions.assertThat(count).hasValue(6);
    }
  }

  static class TestMeta {
    @JsonProperty protected String someString;
    @JsonProperty protected FactMeta meta;
  }

  static class ExampleMeta extends TestMeta {
    ExampleMeta() {
      someString = "oink";
      meta = new FactMeta();
      meta.put("foo", "bar");
      meta.put("foo", "baz");
      meta.put("single", "value");
      meta.put("someNull", null);
      meta.put("otherNull", null);
      meta.put("otherNull", null);
    }
  }
}
