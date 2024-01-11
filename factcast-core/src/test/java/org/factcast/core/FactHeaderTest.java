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
package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FactHeaderTest {

  @Test
  void testDeserializability() throws Exception {
    FactHeader h =
        new ObjectMapper()
            .readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"meta\":{\"foo\":\"bar\"}}",
                FactHeader.class);
    assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
    assertEquals("ns", h.ns());
    assertEquals("t", h.type());
    assertEquals("bar", h.meta().get("foo"));
  }

  @Test
  void testIgnoreExtra() throws Exception {
    FactHeader h =
        new ObjectMapper()
            .readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"bing\":\"bang\"}",
                FactHeader.class);
    assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
    assertEquals("ns", h.ns());
    assertEquals("t", h.type());
  }

  @Test
  void testSerialAccess() throws Exception {
    FactHeader h =
        new ObjectMapper()
            .readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"meta\":{\"_ser\":5}}",
                FactHeader.class);
    assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
    assertEquals(5, h.serial());
  }

  @Test
  void testSerialMissingAccess() throws Exception {
    FactHeader h =
        new ObjectMapper()
            .readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\"}",
                FactHeader.class);
    assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
    assertNull(h.serial());
  }

  @Test
  void testTimestampAccess() throws Exception {
    FactHeader h =
        new ObjectMapper()
            .readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"meta\":{\"_ts\":5}}",
                FactHeader.class);
    assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
    assertEquals(5, h.timestamp());
  }

  @Test
  void testTimestampMissingAccess() throws Exception {
    FactHeader h =
        new ObjectMapper()
            .readValue(
                "{\"id\":\"5d0e3ae9-6684-42bc-87a7-854f76506f7e\",\"ns\":\"ns\",\"type\":\"t\",\"meta\":{\"_xy\":5}}",
                FactHeader.class);
    assertEquals(UUID.fromString("5d0e3ae9-6684-42bc-87a7-854f76506f7e"), h.id());
    assertNull(h.timestamp());
  }
}
