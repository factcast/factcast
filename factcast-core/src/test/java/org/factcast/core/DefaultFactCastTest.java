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

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefaultFactCastTest {

  @Mock private FactStore store;

  @InjectMocks private DefaultFactCast uut;

  @Captor private ArgumentCaptor<UUID> cuuid;

  @Captor private ArgumentCaptor<SubscriptionRequestTO> csr;

  @Captor private ArgumentCaptor<List<Fact>> cfacts;

  @Test
  void testSubscribeEphemeral() {
    when(store.subscribe(csr.capture(), any())).thenReturn(mock(Subscription.class));
    final UUID since = UUID.randomUUID();
    SubscriptionRequest r =
        SubscriptionRequest.follow(FactSpec.ns("foo"))
            .or(FactSpec.ns("some").type("type"))
            .from(since);
    uut.subscribeEphemeral(r, f -> {});
    verify(store).subscribe(any(), any());
    final SubscriptionRequestTO req = csr.getValue();
    assertTrue(req.continuous());
    assertEquals(since, req.startingAfter().orElse(null));
    assertFalse(req.ephemeral());
  }

  @Test
  void testPublish() {
    doNothing().when(store).publish(cfacts.capture());
    final TestFact f = new TestFact();
    uut.publish(f);
    final List<Fact> l = cfacts.getValue();
    assertEquals(1, l.size());
    assertTrue(l.contains(f));
  }

  @Test
  void testNoId() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> uut.publish(new TestFact().id(null)));
  }

  @Test
  void testNoNamespace() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> uut.publish(new TestFact().ns(null)));
  }

  @Test
  void testPublishOneNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.publish((Fact) null));
  }

  @Test
  void testPublishWithAggregatedException() {
    try {
      uut.publish(mock(Fact.class));
      fail();
    } catch (FactValidationException e) {
      assertThat(e.getMessage()).contains("lacks required namespace");
      assertThat(e.getMessage()).contains("lacks required id");
    }
  }

  @Test
  void testPublishManyNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.publish((List<Fact>) null));
  }

  @Test
  void testSubscribeFactsNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.subscribeEphemeral(null, null));
  }

  @Test
  void testSubscribeFacts1stArgNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> uut.subscribeEphemeral(null, f -> {}));
  }

  @Test
  void testSubscribeFacts2ndArgNull() {
    Assertions.assertThrows(
        NullPointerException.class,
        () ->
            uut.subscribeEphemeral(
                SubscriptionRequest.follow(FactSpec.ns("foo")).fromScratch(), null));
  }

  @Test
  void testDefaultFactCast() {
    Assertions.assertThrows(NullPointerException.class, () -> new DefaultFactCast(null));
  }

  @Test
  void testSerialOf() {
    when(store.serialOf(any(UUID.class))).thenReturn(OptionalLong.empty());
    UUID id = UUID.randomUUID();
    uut.serialOf(id);
    verify(store).serialOf(same(id));
  }

  @Test
  void testSerialOfNull() {
    Assertions.assertThrows(NullPointerException.class, () -> uut.serialOf(null));
  }

  @Test
  public void testEnumerateNamespaces() {
    Set<String> set = new HashSet<>();
    when(store.enumerateNamespaces()).thenReturn(set);

    assertSame(set, FactCast.from(store).enumerateNamespaces());
  }

  @Test
  public void testEnumerateTypesNullContract() {
    assertThrows(NullPointerException.class, () -> FactCast.from(store).enumerateTypes(null));
  }

  @Test
  public void testEnumerateTypes() {
    Set<String> test = new HashSet<>();
    when(store.enumerateTypes("test")).thenReturn(test);
    assertSame(test, FactCast.from(store).enumerateTypes("test"));
  }

  @Test
  public void testLockNullContract() {
    assertThrows(NullPointerException.class, () -> uut.lock((List<FactSpec>) null));
  }

  @Test
  public void testLockNamespaceMustNotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> uut.lock(" "));
  }

  @Test
  public void testSubscribeNullContracts() {
    assertThrows(NullPointerException.class, () -> uut.subscribe(null, mock(FactObserver.class)));
    assertThrows(NullPointerException.class, () -> uut.subscribe(null, null));
    assertThrows(
        NullPointerException.class, () -> uut.subscribe(mock(SubscriptionRequest.class), null));
  }

  @Test
  public void testSubscribeClosesDelegate() throws Exception {

    Subscription sub = mock(Subscription.class);
    when(store.subscribe(any(), any())).thenReturn(sub);

    Subscription s =
        uut.subscribe(SubscriptionRequest.follow(FactSpec.ns("test")).fromScratch(), element -> {});
    s.close();
    verify(sub).close();
  }

  @Test
  public void testSubscribeReconnectsOnError() throws Exception {

    Subscription sub1 = mock(Subscription.class);
    Subscription sub2 = mock(Subscription.class);
    ArgumentCaptor<FactObserver> observer = ArgumentCaptor.forClass(FactObserver.class);
    when(store.subscribe(any(), observer.capture())).thenReturn(sub1).thenReturn(sub2);

    List<Fact> seen = new LinkedList<>();

    Subscription s =
        uut.subscribe(SubscriptionRequest.follow(FactSpec.ns("test")).fromScratch(), seen::add);

    FactObserver fo = observer.getValue();
    fo.onNext(new TestFact());
    fo.onNext(new TestFact());

    verify(store).subscribe(any(), any());

    fo.onError(new RuntimeException());

    Thread.sleep(200);
    // subscription must be closed by now...
    verify(sub1).close();
    // and a reconnect should have happened
    verify(store, times(2)).subscribe(any(), any());

    fo.onNext(new TestFact());
    fo.onNext(new TestFact());

    s.close();
    verify(sub2).close();
    assertThat(seen).hasSize(4);
  }
}
