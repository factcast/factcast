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
package org.factcast.store.test;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.junit.jupiter.api.*;

@SuppressWarnings("ALL")
public abstract class AbstractTokenStoreTest {

    TokenStore uut;

    @BeforeEach
    public void setup() {
        uut = createTokenStore();
    }

    @Test
    public void invalidateShouldIgnoreUnknownTokens() throws Exception {
        uut.invalidate(new StateToken(UUID.randomUUID()));
    }

    protected abstract TokenStore createTokenStore();

    @Test
    public void invalidateShouldRemoteToken() throws Exception {
        StateToken token = uut.create(new HashMap<>(), Optional.of("foo"));
        uut.invalidate(token);
        assertThat(uut.getNs(token)).isNotPresent();
        assertThat(uut.getState(token)).isNotPresent();

    }

    @Test
    public void tokenMustMaintainNamespace() throws Exception {
        StateToken token = uut.create(new HashMap<>(), Optional.of("123123"));
        assertThat(uut.getNs(token)).isPresent();
        assertThat(uut.getNs(token).get()).isEqualTo("123123");

    }

    @Test
    public void createShouldActuallyCreateARecord() throws Exception {
        StateToken token = uut.create(new HashMap<>(), Optional.of("foo"));

        assertThat(uut.getNs(token)).isPresent();
        assertThat(uut.getNs(token).get()).isEqualTo("foo");
        assertThat(uut.getState(token)).isPresent();

        // and is not deleted as an effect of get
        assertThat(uut.getState(token)).isPresent();
        assertThat(uut.getNs(token)).isPresent();

    }

    @Test
    public void getStateShouldReturnAbsentForUnknownToken() throws Exception {
        assertThat(!uut.getState(new StateToken(UUID.randomUUID())).isPresent());
    }

    @Test
    public void getNsShouldReturnAbsentForUnknownToken() throws Exception {
        assertThat(!uut.getNs(new StateToken(UUID.randomUUID())).isPresent());
    }

    @Test
    public void testCreateNullContract() throws Exception {
        assertThrows(NullPointerException.class, () -> uut.create(null, null));
        assertThrows(NullPointerException.class, () -> uut.create(null, Optional.of("foo")));

        uut.create(new HashMap<>(), Optional.empty());
    }

    @Test
    public void testInvalidateNullContract() throws Exception {
        assertThrows(NullPointerException.class, () -> uut.invalidate(null));
    }

}
