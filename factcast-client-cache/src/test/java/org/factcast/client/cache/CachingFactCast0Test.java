package org.factcast.client.cache;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

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
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CachingFactCast0Test {
    @Mock
    CachingFactLookup l;

    @Mock
    FactCast fc;

    @Captor
    ArgumentCaptor<IdObserver> obsCap;

    final Fact f = DefaultFact.of("{\"ns\":\"foo\",\"id\":\"" + UUID.randomUUID() + "\"}", "{}");

    CachingFactCast uut;

    @Before
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

    @Test(expected = NullPointerException.class)
    public void testPublishNull() {
        uut.publish((Fact) null);
    }

    @Test(expected = NullPointerException.class)
    public void testPublishNullArgs() {
        uut.publish((List<Fact>) null);
    }

    @Test
    public void testSubscribeToIds() {
        SubscriptionRequest rs = SubscriptionRequest.follow(FactSpec.forMark()).fromScratch();

        final IdObserver observer = id -> {
        };
        uut.subscribeToIds(rs, observer);

        verify(fc).subscribeToIds(same(rs), same(observer));
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToIdsNullParam() {
        uut.subscribeToIds(null, f -> {
        });
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToIdsNullParams() {
        uut.subscribeToIds(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToIdsNullObserverParam() {
        uut.subscribeToIds(mock(SubscriptionRequest.class), null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToFactsNullParam() {
        uut.subscribeToFacts(null, f -> {
        });
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToFactsNullParams() {
        uut.subscribeToFacts(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToFactsNullObserverParam() {
        uut.subscribeToFacts(mock(SubscriptionRequest.class), null);
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

    @Test(expected = NullPointerException.class)
    public void testSerialOfNull() {
        uut.serialOf(null);
    }
}
