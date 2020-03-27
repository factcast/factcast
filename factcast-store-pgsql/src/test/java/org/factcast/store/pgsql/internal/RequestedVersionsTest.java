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
package org.factcast.store.pgsql.internal;

import static org.assertj.core.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

public class RequestedVersionsTest {

    RequestedVersions uut = new RequestedVersions();

    @Test
    public void testEmpty() throws Exception {
        Set<Integer> set = uut.get("foo", "bar");

        assertThat(set).isNotNull().isEmpty();
    }

    @Test
    public void testHappyPath() throws Exception {
        uut.add("foo", "bar", 1);
        uut.add("foo", "bar", 2);
        Set<Integer> set = uut.get("foo", "bar");

        assertThat(set).isNotEmpty().contains(1, 2).hasSize(2);
    }

    @Test
    public void testHappyPathMulti() throws Exception {
        uut.add("foo", "bar", 1);
        uut.add("foo", "baz", 2);
        assertThat(uut.get("foo", "bar")).isNotEmpty().contains(1).hasSize(1);
        assertThat(uut.get("foo", "baz")).isNotEmpty().contains(2).hasSize(1);
        assertThat(uut.get("foo", "boo")).isEmpty();
    }

    @Test
    public void testDontCare() throws Exception {
        assertThat(uut.dontCare("foo", "bar")).isTrue();
    }

    @Test
    public void testDontCare_byRequesting0() throws Exception {
        uut.add("foo", "bar", 0);
        assertThat(uut.dontCare("foo", "bar")).isTrue();
    }

    @Test
    public void testDontCare_negative() throws Exception {
        uut.add("foo", "bar", 7);
        assertThat(uut.dontCare("foo", "bar")).isFalse();
    }

    @Test
    public void testDontCare_byRequesting0NextToOthers() throws Exception {
        uut.add("foo", "bar", 3);
        uut.add("foo", "bar", 0);
        uut.add("foo", "bar", 1);
        assertThat(uut.dontCare("foo", "bar")).isTrue();
    }

    @Test
    public void testExactVersion() throws Exception {
        uut.add("foo", "bar", 3);
        assertThat(uut.exactVersion("foo", "bar", 3)).isTrue();
        assertThat(uut.exactVersion("foo", "bar", 1)).isFalse();
    }

    @Test
    public void testExactVersion_nextToOthers() throws Exception {
        uut.add("foo", "bar", 3);
        uut.add("foo", "bar", 0);
        uut.add("foo", "bar", 1);
        assertThat(uut.exactVersion("foo", "bar", 3)).isTrue();
        assertThat(uut.exactVersion("foo", "bar", 1)).isTrue();
        assertThat(uut.exactVersion("foo", "bar", 5)).isFalse();
    }

}
