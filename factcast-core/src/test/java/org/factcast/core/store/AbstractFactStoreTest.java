/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.core.store;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.util.Lists;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AbstractFactStoreTest {

  TokenStore tokenStore;
  AbstractFactStore uut;

  @BeforeEach
  void setUp() {
    tokenStore = mock(TokenStore.class);
    uut =
        mock(
            AbstractFactStore.class,
            Mockito.withSettings()
                .useConstructor(tokenStore)
                .defaultAnswer(Mockito.CALLS_REAL_METHODS));
  }

  @Test
  void shouldPublishWithEmptyToken() {
    boolean published = uut.publishIfUnchanged(Lists.emptyList(), Optional.empty());
    verify(uut).publish(any());
    assert published;
  }

  @Test
  void shouldNotPublishIfStateUnknown() {
    StateToken token = new StateToken(UUID.randomUUID());
    when(tokenStore.get(eq(token))).thenReturn(Optional.empty());
    boolean published = uut.publishIfUnchanged(Lists.emptyList(), Optional.of(token));
    verify(uut, never()).publish(any());
    assert !published;
  }

  @Test
  void shouldNotPublishIfStateChanged() {
    StateToken token = new StateToken(UUID.randomUUID());
    long lastSerial = 1L;
    long notZero = 2L;
    State state = State.of(Lists.emptyList(), lastSerial);
    when(tokenStore.get(eq(token))).thenReturn(Optional.of(state));
    when(uut.getStateFor(Lists.emptyList(), lastSerial))
        .thenReturn(State.of(Lists.emptyList(), notZero));
    boolean published = uut.publishIfUnchanged(Lists.emptyList(), Optional.of(token));
    verify(uut, never()).publish(any());
    verify(uut).invalidate(eq(token));
    assert !published;
  }

  @Test
  void shouldPublishIfStateUnchanged() {
    StateToken token = new StateToken(UUID.randomUUID());
    long lastSerial = 1L;
    long zero = 0L;
    State state = State.of(Lists.emptyList(), lastSerial);
    when(tokenStore.get(eq(token))).thenReturn(Optional.of(state));
    when(uut.getStateFor(Lists.emptyList(), lastSerial))
        .thenReturn(State.of(Lists.emptyList(), zero));
    boolean published = uut.publishIfUnchanged(Lists.emptyList(), Optional.of(token));
    verify(uut).publish(any());
    verify(uut).invalidate(eq(token));
    assert published;
  }

  @Test
  void shouldCallInvalidateOnTokenStore() {
    StateToken token = new StateToken(UUID.randomUUID());
    uut.invalidate(token);
    verify(tokenStore).invalidate(eq(token));
  }

  @Test
  void shouldCallCreateOnTokenStore() {
    List<FactSpec> specs = Lists.emptyList();
    State state = State.of(Lists.emptyList(), 1L);
    when(uut.getStateFor(eq(specs))).thenReturn(state);
    uut.stateFor(specs);
    verify(tokenStore).create(eq(state));
  }

  @Test
  void stateShouldBeUnchanged() {
    State state = spy(State.of(Lists.emptyList(), 1L));
    State notChanged = State.of(Lists.emptyList(), 0L);
    // lenient as we overload getStateFor and mockito complains
    lenient().doReturn(notChanged).when(uut).getStateFor(any());
    boolean isStateUnchanged = uut.isStateUnchanged(state);
    verify(state).serialOfLastMatchingFact();
    assert isStateUnchanged;
  }

  @Test
  void stateShouldNotBeUnchanged() {
    State state = spy(State.of(Lists.emptyList(), 1L));
    State notChanged = State.of(Lists.emptyList(), 1L);
    // lenient as we overload getStateFor and mockito complains
    lenient().doReturn(notChanged).when(uut).getStateFor(any());
    boolean isStateUnchanged = uut.isStateUnchanged(state);
    verify(state).serialOfLastMatchingFact();
    assert !isStateUnchanged;
  }

  @Test
  void shouldDefaultToAbstractImplementation() {
    List<FactSpec> specs = Lists.list(FactSpec.ns("test"));

    uut.getStateFor(specs, 1L);

    verify(uut).getStateFor(eq(specs));
  }

  @Test
  void getCurrentStateForDelegatesAndStoresState() {
    List<FactSpec> specs =
        Arrays.asList(
            FactSpec.ns("ns").type("foo").version(1), FactSpec.ns("ns").type("bar").version(3));
    State state = new State().specs(specs).serialOfLastMatchingFact(21);
    when(uut.getCurrentStateFor(any())).thenReturn(state);

    uut.currentStateFor(specs);

    verify(tokenStore).create(state);
  }
}
