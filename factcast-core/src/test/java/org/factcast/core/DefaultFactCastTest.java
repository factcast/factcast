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
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultFactCastTest {

  @Mock private FactStore store;

  @InjectMocks private DefaultFactCast uut;

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
    TestFact testFact = new TestFact().id(null);
    Assertions.assertThrows(IllegalArgumentException.class, () -> uut.publish(testFact));
  }

  @Test
  void testNoNamespace() {
    TestFact f = new TestFact().ns(null);
    Assertions.assertThrows(IllegalArgumentException.class, () -> uut.publish(f));
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
  void testSerialOf() {
    when(store.serialOf(any(UUID.class))).thenReturn(OptionalLong.empty());
    UUID id = UUID.randomUUID();
    uut.serialOf(id);
    verify(store).serialOf(same(id));
  }

  @Test
  void testEnumerateNamespaces() {
    Set<String> set = new HashSet<>();
    when(store.enumerateNamespaces()).thenReturn(set);

    assertSame(set, FactCast.from(store).enumerateNamespaces());
  }

  @Test
  void testEnumerateTypes() {
    Set<String> test = new HashSet<>();
    when(store.enumerateTypes("test")).thenReturn(test);
    assertSame(test, FactCast.from(store).enumerateTypes("test"));
  }

  @Test
  void testLockNamespaceMustNotBeEmpty() {
    assertThrows(IllegalArgumentException.class, () -> uut.lock(" "));
  }

  @Test
  void testSubscribeClosesDelegate() throws Exception {

    Subscription sub = mock(Subscription.class);
    when(store.subscribe(any(), any())).thenReturn(sub);

    Subscription s =
        uut.subscribe(SubscriptionRequest.follow(FactSpec.ns("test")).fromScratch(), element -> {});
    s.close();
    verify(sub).close();
  }
}
