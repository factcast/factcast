package org.factcast.client.cache;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Matchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
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
import org.mockito.runners.MockitoJUnitRunner;

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
    public void testFetchById() throws Exception {
        CachingFactCast uut = new CachingFactCast(fc, l);
        when(l.lookup(any())).thenReturn(Optional.of(f));

        Optional<Fact> of = uut.fetchById(UUID.randomUUID());

        assertTrue(of.isPresent());
        assertSame(f, of.get());
        verify(l).lookup(any(UUID.class));

    }

    @Test
    public void testPublish() throws Exception {

        List<Fact> facts = Arrays.asList(f);

        uut.publish(facts);

        verify(fc).publish(same(facts));
    }

    @Test(expected = NullPointerException.class)
    public void testPublishNull() throws Exception {
        uut.publish((Fact) null);
    }

    @Test(expected = NullPointerException.class)
    public void testPublishNullArgs() throws Exception {
        uut.publish((List<Fact>) null);
    }

    @Test
    public void testSubscribeToIds() throws Exception {
        SubscriptionRequest rs = SubscriptionRequest.follow(FactSpec.forMark()).fromScratch();

        final IdObserver observer = id -> {
        };
        uut.subscribeToIds(rs, observer);

        verify(fc).subscribeToIds(same(rs), same(observer));
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToIdsNullParam() throws Exception {
        uut.subscribeToIds(null, f -> {
        });
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToIdsNullParams() throws Exception {
        uut.subscribeToIds(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToIdsNullObserverParam() throws Exception {
        uut.subscribeToIds(mock(SubscriptionRequest.class), null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToFactsNullParam() throws Exception {
        uut.subscribeToFacts(null, f -> {
        });
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToFactsNullParams() throws Exception {
        uut.subscribeToFacts(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeToFactsNullObserverParam() throws Exception {
        uut.subscribeToFacts(mock(SubscriptionRequest.class), null);
    }

    @Test
    public void testSubscribeToFacts() throws Exception {
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
    public void testSerialOf() throws Exception {
        UUID id = UUID.randomUUID();
        uut.serialOf(id);
        verify(fc).serialOf(same(id));
    }

    @Test(expected = NullPointerException.class)
    public void testSerialOfNull() throws Exception {
        uut.serialOf(null);
    }
}
