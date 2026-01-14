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
package org.factcast.factus.event;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;
import java.util.function.BiConsumer;
import lombok.SneakyThrows;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class MetaMapTest {

  @Nested
  class WhenDeserializing {

    @SneakyThrows
    @Test
    void matchesExpectation() {
      String json =
          "{\"someString\":\"oink\",\"meta\":{\"single\":\"value\",\"someNull\":null,\"otherNull\":[null,null],\"foo\":[\"bar\",\"baz\"]}}";
      TestMeta deser = new ObjectMapper().readerFor(TestMeta.class).readValue(json);
      Assertions.assertThat(deser.someString).isEqualTo("oink");
      Assertions.assertThat(deser.meta.keySet()).hasSize(4); // unique keys
      Assertions.assertThat(deser.meta.getFirst("foo")).isEqualTo("bar");
      Assertions.assertThat(deser.meta.getFirst("single")).isEqualTo("value");
      Assertions.assertThat(deser.meta.getAll("foo"))
          .hasSize(2)
          .containsAll(Lists.newArrayList("bar", "baz"));
    }
  }

  @Nested
  class WhenSeserializing {

    @SneakyThrows
    @Test
    void matchesExpectation() {
      String json =
          "{\"someString\":\"oink\",\"meta\":{\"single\":\"value\",\"foo\":[\"bar\",\"baz\"]}}";
      String ser = new ObjectMapper().writeValueAsString(new ExampleMeta());
      Assertions.assertThat(ser).isEqualTo(json);
    }
  }

  static class TestMeta {
    @JsonProperty protected String someString;
    @JsonProperty protected MetaMap meta;
  }

  static class ExampleMeta extends TestMeta {
    ExampleMeta() {
      someString = "oink";
      meta = new MetaMap();
      meta.add("foo", "bar");
      meta.add("foo", "baz");
      meta.add("single", "value");
    }
  }

  @Nested
  class WhenOfing {

    @Test
    void pairExists() {
      Assertions.assertThat(MetaMap.of("A", "B").getFirst("A")).isEqualTo("B");
    }

    @Test
    void pairsExists() {
      MetaMap uut = MetaMap.of("A", "B", "C", "D");
      Assertions.assertThat(uut.getFirst("A")).isEqualTo("B");
      Assertions.assertThat(uut.getFirst("C")).isEqualTo("D");
    }
  }

  @Nested
  class WhenGettingFirst {

    @Test
    void picksFirst() {
      MetaMap uut = MetaMap.of("A", "B", "A", "C");
      Assertions.assertThat(uut.getFirst("A")).isEqualTo("B");
      Assertions.assertThat(uut.getAll("A")).containsExactly("B", "C");
    }
  }

  @Nested
  class WhenGettingAll {

    @Test
    void containsAll() {
      MetaMap uut = MetaMap.of("A", "B", "A", "C");
      Assertions.assertThat(uut.getAll("A")).containsExactly("B", "C");
    }
  }

  @Nested
  class WhenRemoving {

    @Test
    void noneLeft() {
      MetaMap uut = MetaMap.of("A", "1", "B", "2");
      uut.remove("A");
      Assertions.assertThat(uut.getAll("A")).isEmpty();
      Assertions.assertThat(uut.getAll("B")).isNotEmpty();
    }
  }

  @Nested
  class WhenKeyingSet {
    @Test
    void allKeysMetOnce() {
      MetaMap uut = MetaMap.of("A", "1", "B", "2");
      uut.add("C", "x");
      uut.add("C", "y");
      uut.add("D", "z");

      Assertions.assertThat(uut.keySet()).containsExactlyInAnyOrder("A", "B", "C", "D").hasSize(4);
    }
  }

  @Nested
  class WhenAdding {

    @Test
    void createsInitial() {
      MetaMap uut = MetaMap.of("A", "1");
      uut.add("C", "x");

      Assertions.assertThat(uut.getFirst("C")).isEqualTo("x");
    }

    @Test
    void appends() {
      MetaMap uut = MetaMap.of("A", "1");
      uut.add("C", "x");
      uut.add("C", "y");
      uut.add("C", "z");

      Assertions.assertThat(uut.getAll("C")).containsExactly("x", "y", "z");
    }
  }

  @Nested
  class WhenIterating {

    @Test
    void forEachEntry() {
      MetaMap uut = MetaMap.of("A", "1");
      uut.add("C", "x");
      uut.add("C", "y");

      BiConsumer<String, String> mock = mock(BiConsumer.class);
      uut.forEachEntry(mock);

      verify(mock).accept("A", "1");
      verify(mock).accept("C", "x");
      verify(mock).accept("C", "y");
      verifyNoMoreInteractions(mock);
    }

    @Test
    void forEachDistinctKey() {
      MetaMap uut = MetaMap.of("A", "1");
      uut.add("C", "x");
      uut.add("C", "y");

      BiConsumer<String, Collection<String>> mock = mock(BiConsumer.class);
      uut.forEachDistinctKey(mock);
      verify(mock).accept("A", Lists.newArrayList("1"));
      verify(mock).accept("C", Lists.newArrayList("x", "y"));
      verifyNoMoreInteractions(mock);
    }
  }
}
