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
package org.factcast.store.inmem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ExecutorService;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStoreTest;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class InMemFactStoreTest extends AbstractFactStoreTest {

    private InMemFactStore store;

    @Override
    protected FactStore createStoreToTest() {
        this.store = new InMemFactStore();
        return store;
    }

    @Test
    void testDestroy() throws Exception {
        ExecutorService es = mock(ExecutorService.class);
        InMemFactStore inMemFactStore = new InMemFactStore(es);
        inMemFactStore.shutdown();
        verify(es).shutdown();
    }

    @Test
    public void testInMemFactStoreExecutorServiceNullConstructor() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new InMemFactStore(null);
        });
    }

}
