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
package org.factcast.store.registry.transformation.cache;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;
import org.factcast.core.Fact;
import org.junit.jupiter.api.Test;

class KeyTest {

  @Test
  void of() {
    String chainId = "[3-2-1]";
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");

    var ofId = TransformationCache.Key.of(fact.id(), fact.version(), chainId);

    assertTrue(ofId.id().contains(fact.id().toString()));
    assertTrue(ofId.id().contains(String.valueOf(fact.version())));
    assertTrue(ofId.id().contains(chainId));
  }
}
