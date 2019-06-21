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
package org.factcast.core.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ExceptionAfterPublishTest {

    @Test
    public void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new ExceptionAfterPublish(null, new RuntimeException());
        });
        assertThrows(NullPointerException.class, () -> {
            new ExceptionAfterPublish(new LinkedList<>(), null);
        });
        assertThrows(NullPointerException.class, () -> {
            new ExceptionAfterPublish(null, null);
        });

    }

    @Test
    public void testExceptionAfterPublish() throws Exception {
        Throwable e = Mockito.mock(Exception.class);
        List facts = new LinkedList<>();
        ExceptionAfterPublish uut = new ExceptionAfterPublish(facts, e);
        assertThat(uut.publishedFacts()).isSameAs(facts);
        assertThat(uut.getCause()).isSameAs(e);

    }

}
