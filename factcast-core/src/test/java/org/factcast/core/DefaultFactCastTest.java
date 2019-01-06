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
package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import org.assertj.core.util.Sets;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class DefaultFactCastTest {

    @Mock
    private FactStore store;

    @InjectMocks
    private DefaultFactCast uut;

    @Captor
    private ArgumentCaptor<UUID> cuuid;

    @Captor
    private ArgumentCaptor<SubscriptionRequestTO> csr;

    @Captor
    private ArgumentCaptor<List<Fact>> cfacts;

    @Test
    void testSubscribeToFacts() {
        when(store.subscribe(csr.capture(), any())).thenReturn(mock(Subscription.class));
        final UUID since = UUID.randomUUID();
        SubscriptionRequest r = SubscriptionRequest.follow(FactSpec.forMark())
                .or(FactSpec.ns("some").type("type"))
                .from(since);
        uut.subscribeToFacts(r, f -> {
        });
        verify(store).subscribe(any(), any());
        final SubscriptionRequestTO req = csr.getValue();
        assertTrue(req.continuous());
        assertFalse(req.idOnly());
        assertEquals(since, req.startingAfter().get());
        assertFalse(req.ephemeral());
    }

    @Test
    void testSubscribeToIds() {
        when(store.subscribe(csr.capture(), any())).thenReturn(mock(Subscription.class));
        SubscriptionRequest r = SubscriptionRequest.follow(FactSpec.forMark())
                .or(FactSpec.ns("some").type("type"))
                .fromScratch();
        uut.subscribeToIds(r, f -> {
        });
        verify(store).subscribe(any(), any());
        final SubscriptionRequestTO req = csr.getValue();
        assertTrue(req.continuous());
        assertTrue(req.idOnly());
        assertFalse(req.ephemeral());
    }

    @Test
    void testFetchById() {
        when(store.fetchById(cuuid.capture())).thenReturn(Optional.empty());
        final UUID id = UUID.randomUUID();
        uut.fetchById(id);
        assertSame(id, cuuid.getValue());
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
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            uut.publish(new TestFact().id(null));
        });
    }

    @Test
    void testNoNamespace() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            uut.publish(new TestFact().ns(null));
        });
    }

    @Test
    void testPublishOneNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((Fact) null);
        });
    }

    @Test
    void testPublishManyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((List<Fact>) null);
        });
    }

    @Test
    void testFetchByIdNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.fetchById(null);
        });
    }

    @Test
    void testSubscribeIdsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, null);
        });
    }

    @Test
    void testSubscribeFactsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, null);
        });
    }

    @Test
    void testSubscribeIds1stArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, f -> {
            });
        });
    }

    @Test
    void testSubscribeFacts1stArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, f -> {
            });
        });
    }

    @Test
    void testSubscribeIds2ndArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(SubscriptionRequest.follow(FactSpec.forMark()).fromScratch(), null);
        });
    }

    @Test
    void testSubscribeFacts2ndArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(SubscriptionRequest.follow(FactSpec.forMark()).fromScratch(),
                    null);
        });
    }

    @Test
    void testDefaultFactCast() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new DefaultFactCast(null);
        });
    }

    @Test
    void testpublishWithMarkOneNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publishWithMark((Fact) null);
        });
    }

    @Test
    void testpublishWithMarkManyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publishWithMark((List<Fact>) null);
        });
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
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.serialOf(null);
        });
    }

    @Test
    public void testEnumerateNamespaces() throws Exception {
        Set<String> set = new HashSet<>();
        when(store.enumerateNamespaces()).thenReturn(set);

        assertSame(set, FactCast.from(store).enumerateNamespaces());
    }

    @Test
    public void testEnumerateTypesNullContract() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            FactCast.from(store).enumerateTypes(null);
        });
    }

    @Test
    public void testEnumerateTypes() throws Exception {
        Set<String> test = new HashSet<>();
        LinkedHashSet<String> other = Sets.newLinkedHashSet("foo");

        when(store.enumerateTypes("test")).thenReturn(test);
        assertSame(test, FactCast.from(store).enumerateTypes("test"));

    }
}
