package org.factcast.client.cache;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
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
public class CachingFactCastTest {
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
    public void testNonNull() throws Exception {
        uut.publish((List<Fact>) null);
    }

    @Test
    public void testSubscribeToIds() throws Exception {
        SubscriptionRequest rs = SubscriptionRequest.follow(FactSpec.forMark()).sinceInception();

        final IdObserver observer = id -> {
        };
        uut.subscribeToIds(rs, observer);

        verify(fc).subscribeToIds(same(rs), same(observer));
    }

    @Test
    public void testSubscribeToFacts() throws Exception {
        SubscriptionRequest rs = SubscriptionRequest.follow(FactSpec.forMark()).sinceInception();
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

}
