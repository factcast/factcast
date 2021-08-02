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
package org.factcast.store.registry.validation.schema;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class SchemaSourceTest {

  @Test
  public void testToKey() throws Exception {
    String ns = "ns";
    String type = "type";
    int version = 7;
    SchemaSource uut = new SchemaSource("id", "hash", ns, type, version);
    SchemaKey actual = uut.toKey();

    assertThat(actual.ns()).isEqualTo(ns);
    assertThat(actual.type()).isEqualTo(type);
    assertThat(actual.version()).isEqualTo(version);
  }
}
