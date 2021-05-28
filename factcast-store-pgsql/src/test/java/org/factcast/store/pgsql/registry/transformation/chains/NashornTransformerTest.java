package org.factcast.store.pgsql.registry.transformation.chains;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jdk.nashorn.api.scripting.ScriptObjectMirror;
import org.junit.jupiter.api.Test;

class NashornTransformerTest {

  private NashornTransformer uut = new NashornTransformer();

  @Test
  void fixArrayTransformations_NoFixNeeded() {
    final Map<String, Object> in = new HashMap<>();
    in.put("anInt", 42);
    in.put("aString", "test");
    in.put("aList", Collections.singleton(21));
    final Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("a", "b");
    in.put("anObject", nestedMap);

    uut.fixArrayTransformations(in);

    assertThat(in)
        .containsEntry("anInt", 42)
        .containsEntry("aString", "test")
        .containsEntry("aList", Collections.singleton(21));
    assertThat((Map<String, Object>) in.get("anObject")).containsEntry("a", "b");
  }

  @Test
  void fixArrayTransformations_ArrayFixed() {
    final Map<String, Object> in = new HashMap<>();
    in.put("anInt", 42);
    ScriptObjectMirror array = mock(ScriptObjectMirror.class);
    when(array.isArray()).thenReturn(true);
    when(array.to(List.class)).thenReturn(Collections.singletonList(1));
    in.put("newArray", array);

    uut.fixArrayTransformations(in);

    assertThat(in)
        .containsEntry("anInt", 42)
        .containsEntry("newArray", Collections.singletonList(1));
  }
}
