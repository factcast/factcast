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
package org.factcast.store.registry.transformation.chains;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.SneakyThrows;
import nl.altindag.log.LogCaptor;
import org.factcast.core.subscription.TransformationException;
import org.factcast.script.engine.graaljs.GraalJSEngineCache;
import org.factcast.store.registry.transformation.Transformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraalJsTransformerTest {

  private GraalJsTransformer uut = new GraalJsTransformer(new GraalJSEngineCache());

  private ObjectMapper om = new ObjectMapper();

  @Mock Transformation transformation;

  @Test
  void testTransform() {
    when(transformation.transformationCode())
        .thenReturn(
            Optional.of(
                "function transform(e) {e.displayName = e.name + ' ' + e.age; e.hobbies ="
                    + " [e.hobby]; e.childMap.anotherHobbies = [e.childMap.anotherHobby];}"));

    var data = new HashMap<String, Object>();
    data.put("name", "Hugo");
    data.put("age", 38);
    data.put("hobby", "foo");

    var childMap = new HashMap<String, Object>();
    childMap.put("anotherName", "Ernst");
    childMap.put("anotherHobby", "bar");

    data.put("childMap", childMap);

    var result = uut.transform(transformation, om.convertValue(data, JsonNode.class));

    assertThat(result.get("displayName").asText()).isEqualTo("Hugo 38");
    assertThat(result.get("hobbies").isArray()).isTrue();
    assertThat(result.get("hobbies").get(0).asText()).isEqualTo("foo");
    assertThat(result.get("childMap").get("anotherHobbies").isArray()).isTrue();
    assertThat(result.get("childMap").get("anotherHobbies").get(0).asText()).isEqualTo("bar");
  }

  @Test
  void testTransform_nestedArray() {
    when(transformation.transformationCode())
        .thenReturn(
            Optional.of(
                "function transform(e) {e.newMap = {foo: []}; e.newArray = []; e.oldMap.foo = [];}"));

    var data = new HashMap<String, Object>();
    data.put("oldMap", new HashMap<String, Object>());

    var result = uut.transform(transformation, om.convertValue(data, JsonNode.class));

    assertThat(result.get("newMap").isObject()).isTrue();
    assertThat(result.get("newArray").isArray()).isTrue();
    assertThat(result.get("oldMap").get("foo").isArray()).isTrue();
    assertThat(result.get("newMap").get("foo").isArray()).isTrue();
  }

  @Test
  @SneakyThrows
  void testParallelAccess() {
    when(transformation.transformationCode())
        .thenReturn(
            Optional.of(
                "function transform(e) { \n"
                    + "  console.log('Starting Busy Wait...'); \n"
                    // code to do busy waiting in JS:
                    + "  const date = Date.now();\n"
                    + "  const milliseconds = 2000;\n"
                    + "  let currentDate = null;\n"
                    + "  do {\n"
                    + "    currentDate = Date.now();\n"
                    + "  } while (currentDate - date < milliseconds);\n"
                    + "  console.log('Done busy waiting.'); \n"
                    // actual transformation
                    + "  e.x = e.y; }\n"));

    var d1 = new HashMap<String, Object>();
    d1.put("y", "1");

    var d2 = new HashMap<String, Object>();
    d2.put("y", "2");

    // warm up engine
    uut.transform(transformation, om.convertValue(d1, JsonNode.class));

    Callable<JsonNode> c1 =
        () -> uut.transform(transformation, om.convertValue(d1, JsonNode.class));
    Callable<JsonNode> c2 =
        () -> uut.transform(transformation, om.convertValue(d2, JsonNode.class));

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    try {
      Future<JsonNode> result1 = executor.submit(c1);
      Future<JsonNode> result2 = executor.submit(c2);
      JsonNode n1 = result1.get();
      JsonNode n2 = result2.get();

      assertThat(n1.get("x").asText()).isEqualTo("1");
      assertThat(n2.get("x").asText()).isEqualTo("2");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  @SneakyThrows
  void testParallelAccess_modifyingObjects() {
    when(transformation.transformationCode())
        .thenReturn(Optional.of("function transform(e) { e.foo = {}; e.foo.bar = e.y; }\n"));

    var d1 = new HashMap<String, Object>();
    d1.put("y", "1");

    var d2 = new HashMap<String, Object>();
    d2.put("y", "2");

    // warm up engine
    uut.transform(transformation, om.convertValue(d1, JsonNode.class));

    Callable<JsonNode> c1 =
        () -> uut.transform(transformation, om.convertValue(d1, JsonNode.class));
    Callable<JsonNode> c2 =
        () -> uut.transform(transformation, om.convertValue(d2, JsonNode.class));

    ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(2);
    try {
      Future<JsonNode> result1 = executor.submit(c1);
      Future<JsonNode> result2 = executor.submit(c2);
      JsonNode n1 = result1.get();
      JsonNode n2 = result2.get();

      assertThat(n1.get("y").asText()).isEqualTo("1");
      assertThat(n1.get("foo").get("bar").asText()).isEqualTo("1");
      assertThat(n2.get("y").asText()).isEqualTo("2");
      assertThat(n2.get("foo").get("bar").asText()).isEqualTo("2");
    } finally {
      executor.shutdown();
    }
  }

  @Test
  void logsExceptionBrokenScript() {
    when(transformation.transformationCode())
        .thenReturn(Optional.of("function transform(e) { \n" + "  br0ken code" + " }\n"));

    LogCaptor logCaptor = LogCaptor.forClass(uut.getClass());

    Map<String, Object> d1 = new HashMap<>();
    d1.put("y", "1");
    assertThatThrownBy(
            () -> {
              uut.transform(transformation, om.convertValue(d1, JsonNode.class));
            })
        .isInstanceOf(TransformationException.class);

    assertThat(logCaptor.getLogs().size()).isGreaterThan(0);
    assertThat(logCaptor.getLogs().stream().anyMatch(f -> f.contains("during engine creation")))
        .isTrue();
  }

  @Test
  void logsExceptionBrokenParam() {
    when(transformation.transformationCode())
        .thenReturn(Optional.of("function transform(e) {throw \"fail at runtime\"}"));

    LogCaptor logCaptor = LogCaptor.forClass(uut.getClass());

    Map<String, Object> d1 = new HashMap<>();
    d1.put("y", "1");
    assertThatThrownBy(
            () -> {
              uut.transform(transformation, om.convertValue(d1, JsonNode.class));
            })
        .isInstanceOf(TransformationException.class);

    assertThat(logCaptor.getLogs().size()).isGreaterThan(0);
    assertThat(logCaptor.getLogs().stream().anyMatch(f -> f.contains("during transformation")))
        .isTrue();
  }
}
