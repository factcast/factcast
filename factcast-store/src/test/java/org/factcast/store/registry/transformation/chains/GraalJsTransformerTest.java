package org.factcast.store.registry.transformation.chains;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Optional;
import org.factcast.store.registry.transformation.Transformation;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
class GraalJsTransformerTest {

  private GraalJsTransformer uut = new GraalJsTransformer();

  private ObjectMapper om = new ObjectMapper();

  @Mock Transformation transformation;

  @Test
  void testTransform() {
    when(transformation.transformationCode())
        .thenReturn(
            Optional.of(
                "function transform(e) {e.displayName = e.name + ' ' + e.age; e.hobbies = [e.hobby]; e.childMap.anotherHobbies = [e.childMap.anotherHobby];}"));

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
}
