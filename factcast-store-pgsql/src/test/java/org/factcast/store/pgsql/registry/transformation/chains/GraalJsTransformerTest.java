package org.factcast.store.pgsql.registry.transformation.chains;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.val;
import org.factcast.store.pgsql.registry.transformation.Transformation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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

    val data = new HashMap<String, Object>();
    data.put("name", "Hugo");
    data.put("age", 38);
    data.put("hobby", "foo");

    val childMap = new HashMap<String, Object>();
    childMap.put("anotherName", "Ernst");
    childMap.put("anotherHobby", "bar");

    data.put("childMap", childMap);

    val result = uut.transform(transformation, om.convertValue(data, JsonNode.class));

    assertThat(result.get("displayName").asText()).isEqualTo("Hugo 38");
    assertThat(result.get("hobbies").isArray()).isTrue();
    assertThat(result.get("hobbies").get(0).asText()).isEqualTo("foo");
    assertThat(result.get("childMap").get("anotherHobbies").isArray()).isTrue();
    assertThat(result.get("childMap").get("anotherHobbies").get(0).asText()).isEqualTo("bar");
  }
}
