package org.factcast.client.cache;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
public class CachingFactCast0Test {

    @Mock
    CachingFactLookup l;

    @Mock
    FactCast fc;

    @Captor
    ArgumentCaptor<IdObserver> obsCap;

    final Fact f = DefaultFact.of("{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");

    CachingFactCast uut;

    @BeforeEach
    public void setUp() {
        uut = new CachingFactCast(fc, l);
    }

    @Test
    public void testFetchById() {
        CachingFactCast uut = new CachingFactCast(fc, l);
        when(l.lookup(any())).thenReturn(Optional.of(f));
        Optional<Fact> of = uut.fetchById(UUID.randomUUID());
        assertTrue(of.isPresent());
        assertSame(f, of.get());
        verify(l).lookup(any(UUID.class));
    }

    @Test
    public void testPublish() {
        List<Fact> facts = Collections.singletonList(f);
        uut.publish(facts);
        verify(fc).publish(same(facts));
    }

    @Test
    public void testPublishNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((Fact) null);
        });
    }

    @Test
    public void testPublishNullArgs() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((List<Fact>) null);
        });
    }

    @Test
    public void testSubscribeToIds() {
        SubscriptionRequest rs = SubscriptionRequest.follow(FactSpec.forMark()).fromScratch();
        final IdObserver observer = id -> {
        };
        uut.subscribeToIds(rs, observer);
        verify(fc).subscribeToIds(same(rs), same(observer));
    }

    @Test
    public void testSubscribeToIdsNullParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, f -> {
            });
        });
    }

    @Test
    public void testSubscribeToIdsNullParams() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, null);
        });
    }

    @Test
    public void testSubscribeToIdsNullObserverParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(mock(SubscriptionRequest.class), null);
        });
    }

    @Test
    public void testSubscribeToFactsNullParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, f -> {
            });
        });
    }

    @Test
    public void testSubscribeToFactsNullParams() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, null);
        });
    }

    @Test
    public void testSubscribeToFactsNullObserverParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(mock(SubscriptionRequest.class), null);
        });
    }

    @Test
    public void testSubscribeToFacts() {
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
    public void testSerialOf() {
        UUID id = UUID.randomUUID();
        uut.serialOf(id);
        verify(fc).serialOf(same(id));
    }

    @Test
    public void testSerialOfNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.serialOf(null);
        });
    }
}
