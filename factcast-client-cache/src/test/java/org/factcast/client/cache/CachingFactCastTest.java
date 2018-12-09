package org.factcast.client.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.core.subscription.observer.IdObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CachingFactCastTest {

    @Mock
    CachingFactLookup l;

    @Mock
    FactCast fc;

    @Captor
    ArgumentCaptor<IdObserver> obsCap;

    final Fact f = DefaultFact.of("{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");

    CachingFactCast uut;

    @BeforeEach
    void setUp() {
        uut = new CachingFactCast(fc, l);
    }

    @Test
    void testFetchById() {
        CachingFactCast uut = new CachingFactCast(fc, l);
        when(l.lookup(any())).thenReturn(Optional.of(f));
        Optional<Fact> of = uut.fetchById(UUID.randomUUID());
        assertTrue(of.isPresent());
        assertSame(f, of.get());
        verify(l).lookup(any(UUID.class));
    }

    @Test
    void testPublish() {
        List<Fact> facts = Collections.singletonList(f);
        uut.publish(facts);
        verify(fc).publish(same(facts));
    }

    @Test
    void testPublishNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((Fact) null);
        });
    }

    @Test
    void testPublishNullArgs() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((List<Fact>) null);
        });
    }

    @Test
    void testSubscribeToIds() {
        SubscriptionRequest rs = SubscriptionRequest.follow(FactSpec.forMark()).fromScratch();
        final IdObserver observer = id -> {
        };
        uut.subscribeToIds(rs, observer);
        verify(fc).subscribeToIds(same(rs), same(observer));
    }

    @Test
    void testSubscribeToIdsNullParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, f -> {
            });
        });
    }

    @Test
    void testSubscribeToIdsNullParams() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, null);
        });
    }

    @Test
    void testSubscribeToIdsNullObserverParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(mock(SubscriptionRequest.class), null);
        });
    }

    @Test
    void testSubscribeToFactsNullParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, f -> {
            });
        });
    }

    @Test
    void testSubscribeToFactsNullParams() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, null);
        });
    }

    @Test
    void testSubscribeToFactsNullObserverParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(mock(SubscriptionRequest.class), null);
        });
    }

    @Test
    void testSubscribeToFacts() {
        SubscriptionRequest rs = SubscriptionRequest.follow(FactSpec.forMark()).fromScratch();
        when(l.lookup(any())).thenReturn(Optional.of(f));
        when(fc.subscribeToIds(same(rs), obsCap.capture())).thenReturn(null);
        final FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(rs, observer);
        IdObserver underlyingObserver = obsCap.getValue();
        verify(fc).subscribeToIds(same(rs), any());
        verify(observer, never()).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        // provide an id
        underlyingObserver.onNext(f.id());
        // assume lookup for id
        verify(l).lookup(eq(f.id()));
        // verify signals get through
        underlyingObserver.onCatchup();
        verify(observer).onCatchup();
        underlyingObserver.onComplete();
        verify(observer).onComplete();
        Exception e = new Exception();
        underlyingObserver.onError(e);
        verify(observer).onError(same(e));
    }

    @Test
    void testSerialOf() {
        UUID id = UUID.randomUUID();
        uut.serialOf(id);
        verify(fc).serialOf(same(id));
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
        when(fc.enumerateNamespaces()).thenReturn(set);

        assertSame(set, uut.enumerateNamespaces());
        verify(fc).enumerateNamespaces();

    }

    @Test
    public void testEnumerateTypes() throws Exception {
        Set<String> set = new HashSet<>();
        when(fc.enumerateTypes("foo")).thenReturn(set);

        assertSame(set, uut.enumerateTypes("foo"));
        verify(fc).enumerateTypes("foo");

    }
}
