package org.factcast.core;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DefaultFactCast0Test {
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

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testSubscribeToFacts() throws Exception {
        when(store.subscribe(csr.capture(), any())).thenReturn(mock(Subscription.class));

        final UUID since = UUID.randomUUID();
        SubscriptionRequest r = SubscriptionRequest.follow(FactSpec.forMark())
                .or(FactSpec.ns(
                        "some").type("type"))
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
    public void testSubscribeToIds() throws Exception {
        when(store.subscribe(csr.capture(), any())).thenReturn(mock(Subscription.class));

        SubscriptionRequest r = SubscriptionRequest.follow(FactSpec.forMark())
                .or(FactSpec.ns(
                        "some").type("type"))
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
    public void testFetchById() throws Exception {
        when(store.fetchById(cuuid.capture())).thenReturn(Optional.empty());

        final UUID id = UUID.randomUUID();
        uut.fetchById(id);

        assertSame(id, cuuid.getValue());
    }

    @Test
    public void testPublish() throws Exception {
        doNothing().when(store).publish(cfacts.capture());

        final Test0Fact f = new Test0Fact();
        uut.publish(f);

        final List<Fact> l = cfacts.getValue();
        assertEquals(1, l.size());
        assertTrue(l.contains(f));

    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoId() throws Exception {
        uut.publish(new Test0Fact().id(null));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNoNamespace() throws Exception {
        uut.publish(new Test0Fact().ns(null));
    }

    @Test(expected = NullPointerException.class)
    public void testPublishOneNull() throws Exception {
        uut.publish((Fact) null);
    }

    @Test(expected = NullPointerException.class)
    public void testPublishManyNull() throws Exception {
        uut.publish((List<Fact>) null);
    }

    @Test(expected = NullPointerException.class)
    public void testFetchByIdNull() throws Exception {
        uut.fetchById(null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeIdsNull() throws Exception {
        uut.subscribeToIds(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeFactsNull() throws Exception {
        uut.subscribeToFacts(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeIds1stArgNull() throws Exception {
        uut.subscribeToIds(null, f -> {
        });
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeFacts1stArgNull() throws Exception {
        uut.subscribeToFacts(null, f -> {
        });
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeIds2ndArgNull() throws Exception {
        uut.subscribeToIds(SubscriptionRequest.follow(FactSpec.forMark()).fromScratch(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testSubscribeFacts2ndArgNull() throws Exception {
        uut.subscribeToFacts(SubscriptionRequest.follow(FactSpec.forMark()).fromScratch(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testDefaultFactCast() throws Exception {
        new DefaultFactCast(null);
    }

    @Test(expected = NullPointerException.class)
    public void testpublishWithMarkOneNull() throws Exception {
        uut.publishWithMark((Fact) null);
    }

    @Test(expected = NullPointerException.class)
    public void testpublishWithMarkManyNull() throws Exception {
        uut.publishWithMark((List<Fact>) null);
    }

    @Test
    public void testSerialOf() throws Exception {
        when(store.serialOf(any(UUID.class))).thenReturn(OptionalLong.empty());
        UUID id = UUID.randomUUID();
        uut.serialOf(id);
        verify(store).serialOf(same(id));
    }

    @Test(expected = NullPointerException.class)
    public void testSerialOfNull() throws Exception {
        uut.serialOf(null);
    }
}
