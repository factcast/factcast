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
package org.factcast.store.test;

import java.util.*;

import org.factcast.core.store.State;
import org.factcast.core.store.StateToken;
import org.factcast.core.store.TokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("ALL")
public abstract class AbstractTokenStoreTest {

  protected TokenStore uut;

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
  public void invalidateShouldRemoveToken() throws Exception {
    StateToken token = uut.create(new State());
    uut.invalidate(token);
    assertThat(uut.get(token)).isNotPresent();
  }

  @Test
  public void createShouldActuallyCreateARecord() throws Exception {
    StateToken token = uut.create(new State().serialOfLastMatchingFact(100));

    assertThat(uut.get(token)).isPresent();

    // and is not deleted as an effect of get
    assertThat(uut.get(token)).isPresent();
  }

  @Test
  public void getStateShouldReturnAbsentForUnknownToken() throws Exception {
    assertThat(uut.get(new StateToken(UUID.randomUUID()))).isNotPresent();
  }
}
