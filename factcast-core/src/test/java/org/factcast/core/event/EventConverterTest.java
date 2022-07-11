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
package org.factcast.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;
import lombok.NonNull;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.event.Specification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EventConverterTest {

  @Mock private @NonNull EventSerializer ser;

  @InjectMocks private EventConverter underTest;

  @Test
  void negativeVersionIsIgnored() {
    assertThat(underTest.toFact(new E()).header().version()).isEqualTo(0);
  }

  @Test
  void failsOnNullKeyInMeta() {
    assertThatThrownBy(() -> underTest.toFact(new E2()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsNullValueInMeta() {
    underTest.toFact(new E3());
  }

  @Test
  void discoversType() {
    assertThat(underTest.toFact(new E3()).type()).isEqualTo(E3.class.getSimpleName());
  }

  @Specification(ns = "test", version = -99)
  static class E implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return new HashSet<>();
    }
  }

  @Specification(ns = "test", version = 10)
  static class E3 extends E {
    @Override
    public Map<String, String> additionalMetaMap() {
      HashMap<String, String> m = new HashMap<String, String>();
      m.put("foo", null);
      return m;
    }
  }

  @Specification(ns = "test", version = 10)
  static class E2 extends E {
    @Override
    public Map<String, String> additionalMetaMap() {
      HashMap<String, String> m = new HashMap<String, String>();
      m.put(null, "foo");
      return m;
    }
  }
}
