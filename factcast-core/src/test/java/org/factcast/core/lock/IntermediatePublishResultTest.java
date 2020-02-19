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
package org.factcast.core.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedList;

import org.junit.jupiter.api.Test;

public class IntermediatePublishResultTest {
    @Test
    public void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new IntermediatePublishResult(null);
        });

        assertThrows(NullPointerException.class, () -> {
            new IntermediatePublishResult(new LinkedList<>()).andThen(null);
        });
    }

    @Test
    public void testAndThen() throws Exception {
        IntermediatePublishResult uut = new IntermediatePublishResult(new LinkedList<>()).andThen(
                () -> {
                });
        assertThat(uut.andThen()).isPresent();
    }
}
