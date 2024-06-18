/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.AbstractResource;
import org.springframework.core.io.ByteArrayResource;

public class RegistryIndexTest {

  @Test
  void testDeserialization() throws Exception {
    String json =
        "\n"
            + "{\"schemes\":[{\"id\":\"namespaceA/eventA/1/schema.json\",\"ns\":\"namespaceA\",\"type\":\"eventA\",\"version\":1,\"hash\":\"84e69a2d3e3d195abb986aad22b95ffd\"},{\"id\":\"namespaceA/eventA/2/schema.json\",\"ns\":\"namespaceA\",\"type\":\"eventA\",\"version\":2,\"hash\":\"24d48268356e3cb7ac2f148850e4aac1\"}]}";
    RegistryIndex index = new ObjectMapper().readValue(json, RegistryIndex.class);

    assertEquals("84e69a2d3e3d195abb986aad22b95ffd", index.schemes().get(0).hash());
  }

  @Test
  void wrapsException() {
    AbstractResource r = Mockito.spy(new ByteArrayResource("foo".getBytes()));
    try {
      Mockito.when(r.getInputStream()).thenThrow(new IOException("expected"));
    } catch (IOException e) {
    }

    Assertions.assertThatThrownBy(
            () -> {
              RegistryIndex.fetch(r);
            })
        .isInstanceOf(SchemaRegistryUnavailableException.class);
  }
}
