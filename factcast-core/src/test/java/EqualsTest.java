/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class EqualsTest {

    @org.junit.jupiter.api.Test
    public void inner_objects_should_be_equal_even_if_part_of_a_complex_structure() {
        // We are creating two maps, both containing an entry with the same key
        // but different values
        Map<String, Object> innerMap1 = new HashMap<>();
        innerMap1.put("key", "value1");
        Map<String, Object> innerMap2 = new HashMap<>();
        innerMap2.put("key", "value2");

        // We are creating two sets, each containing one of the corresponding
        // maps
        Set<Map<String, Object>> innerSet1 = new HashSet<>();
        innerSet1.add(innerMap1);
        Set<Map<String, Object>> innerSet2 = new HashSet<>();
        innerSet2.add(innerMap2);

        // We are creating two sets, each containing one of the corresponding
        // sets
        Map<String, Object> outerMap1 = new HashMap<>();
        outerMap1.put("symbol", innerSet1);
        Map<String, Object> outerMap2 = new HashMap<>();
        outerMap2.put("symbol", innerSet2);

        // We are creating two maps, each containing one of the corresponding
        // sets
        Set<Map<String, Object>> outerSet1 = new HashSet<>();
        outerSet1.add(outerMap1);
        Set<Map<String, Object>> outerSet2 = new HashSet<>();
        outerSet2.add(outerMap2);

        assertNotEquals(innerMap1, innerMap2);
        assertNotEquals(innerSet1, innerSet2);
        assertNotEquals(outerMap1, outerMap2);
        assertNotEquals(outerSet1, outerSet2);

        // We are changing the value of the entry of the inner map to match
        // against the value of the corresponding inner map. This
        // should result in having 'equal' inner maps!
        innerMap2.put("key", "value1");

        assertEquals(innerMap1, innerMap2);
        assertEquals(innerSet1, innerSet2);
        assertEquals(outerMap1, outerMap2);
        assertEquals(outerSet1, outerSet2);
    }

}