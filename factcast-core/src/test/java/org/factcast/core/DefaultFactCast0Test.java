package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

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
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
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

    @Test
    public void testSubscribeToFacts() {
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
    public void testSubscribeToIds() {
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
    public void testFetchById() {
        when(store.fetchById(cuuid.capture())).thenReturn(Optional.empty());
        final UUID id = UUID.randomUUID();
        uut.fetchById(id);
        assertSame(id, cuuid.getValue());
    }

    @Test
    public void testPublish() {
        doNothing().when(store).publish(cfacts.capture());
        final Test0Fact f = new Test0Fact();
        uut.publish(f);
        final List<Fact> l = cfacts.getValue();
        assertEquals(1, l.size());
        assertTrue(l.contains(f));
    }

    @Test
    public void testNoId() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            uut.publish(new Test0Fact().id(null));
        });
    }

    @Test
    public void testNoNamespace() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            uut.publish(new Test0Fact().ns(null));
        });
    }

    @Test
    public void testPublishOneNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((Fact) null);
        });
    }

    @Test
    public void testPublishManyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publish((List<Fact>) null);
        });
    }

    @Test
    public void testFetchByIdNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.fetchById(null);
        });
    }

    @Test
    public void testSubscribeIdsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, null);
        });
    }

    @Test
    public void testSubscribeFactsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, null);
        });
    }

    @Test
    public void testSubscribeIds1stArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(null, f -> {
            });
        });
    }

    @Test
    public void testSubscribeFacts1stArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(null, f -> {
            });
        });
    }

    @Test
    public void testSubscribeIds2ndArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToIds(SubscriptionRequest.follow(FactSpec.forMark()).fromScratch(), null);
        });
    }

    @Test
    public void testSubscribeFacts2ndArgNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.subscribeToFacts(SubscriptionRequest.follow(FactSpec.forMark()).fromScratch(),
                    null);
        });
    }

    @Test
    public void testDefaultFactCast() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new DefaultFactCast(null);
        });
    }

    @Test
    public void testpublishWithMarkOneNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publishWithMark((Fact) null);
        });
    }

    @Test
    public void testpublishWithMarkManyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.publishWithMark((List<Fact>) null);
        });
    }

    @Test
    public void testSerialOf() {
        when(store.serialOf(any(UUID.class))).thenReturn(OptionalLong.empty());
        UUID id = UUID.randomUUID();
        uut.serialOf(id);
        verify(store).serialOf(same(id));
    }

    @Test
    public void testSerialOfNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            uut.serialOf(null);
        });
    }
}
