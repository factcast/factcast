package org.factcast.store.test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.MarkFact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;

import lombok.SneakyThrows;

public abstract class AbstractFactStoreTest {

    static final FactSpec ANY = FactSpec.ns("default");

    FactCast uut;

    @Before
    public void setUp() throws Exception {
        uut = FactCast.from(createStoreToTest());
    }

    protected abstract FactStore createStoreToTest();

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStore() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        Subscription s = uut.subscribeToFacts(SubscriptionRequest.catchup(ANY).fromScratch(),
                observer);
        s.awaitComplete();
        verify(observer).onCatchup();
        verify(observer).onComplete();
        verify(observer, never()).onError(any());
        verify(observer, never()).onNext(any());
    }

    @Test(timeout = 10000, expected = IllegalArgumentException.class)
    @DirtiesContext
    public void testUniquenessConstraint() throws Exception {
        final UUID id = UUID.randomUUID();
        uut.publish(Fact.of("{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                "{}"));
        uut.publish(Fact.of("{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                "{}"));
        fail();
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreFollowNonMatching() throws Exception {
        TestFactObserver observer = testObserver();
        uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                .awaitCatchup();
        verify(observer).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        verify(observer, never()).onNext(any());

        uut.publishWithMark(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"other\"}", "{}"));
        uut.publishWithMark(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"other\"}", "{}"));

        observer.await(2);

        // the mark facts only
        verify(observer, times(2)).onNext(any());

        assertEquals(MarkFact.MARK_TYPE, observer.values.get(0).type());
        assertEquals(MarkFact.MARK_TYPE, observer.values.get(1).type());

    }

    private TestFactObserver testObserver() {
        return spy(new TestFactObserver());
    }

    private static class TestFactObserver implements FactObserver {

        private List<Fact> values = new LinkedList<>();

        @Override
        public synchronized void onNext(Fact element) {
            values.add(element);
            this.notifyAll();
        }

        @SneakyThrows
        public void await(int count) {
            synchronized (this) {
                while (true) {
                    if (values.size() >= count) {
                        return;
                    } else {
                        this.wait(50);
                    }
                }
            }
        }

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreFollowMatching() throws Exception {
        TestFactObserver observer = testObserver();
        uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                .awaitCatchup();

        verify(observer).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        verify(observer, never()).onNext(any());

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        observer.await(1);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreEphemeral() throws Exception {

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));

        TestFactObserver observer = testObserver();
        uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromNowOn(), observer).awaitCatchup();

        // nothing recieved

        verify(observer).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        verify(observer, never()).onNext(any());

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        observer.await(1);
        verify(observer, times(1)).onNext(any());
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreEphemeralWithCancel() throws Exception {
        TestFactObserver observer = testObserver();
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));

        Subscription subscription = uut.subscribeToFacts(SubscriptionRequest.follow(ANY)
                .fromNowOn(), observer).awaitCatchup();

        // nothing recieved

        verify(observer).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        verify(observer, never()).onNext(any());

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        observer.await(1);

        verify(observer, times(1)).onNext(any());

        subscription.close();

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        Thread.sleep(100);

        // additional event not received
        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreFollowWithCancel() throws Exception {
        TestFactObserver observer = testObserver();
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));

        Subscription subscription = uut.subscribeToFacts(SubscriptionRequest.follow(ANY)
                .fromScratch(), observer).awaitCatchup();

        // nothing recieved

        verify(observer).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        verify(observer, times(3)).onNext(any());

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        observer.await(4);
        subscription.close();

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        Thread.sleep(100);

        // additional event not received
        verify(observer, times(4)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreCatchupMatching() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.subscribeToFacts(SubscriptionRequest.catchup(ANY).fromScratch(), observer)
                .awaitComplete();

        verify(observer).onCatchup();
        verify(observer).onComplete();
        verify(observer, never()).onError(any());
        verify(observer).onNext(any());
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreFollowMatchingDelayed() throws Exception {
        TestFactObserver observer = testObserver();
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                .awaitCatchup();

        verify(observer).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        verify(observer).onNext(any());

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        observer.await(2);

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testEmptyStoreFollowNonMatchingDelayed() throws Exception {
        TestFactObserver observer = testObserver();
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"t1\"}", "{}"));
        uut.subscribeToFacts(SubscriptionRequest.follow(ANY).fromScratch(), observer)
                .awaitCatchup();

        verify(observer).onCatchup();
        verify(observer, never()).onComplete();
        verify(observer, never()).onError(any());
        verify(observer).onNext(any());

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"other\",\"type\":\"t1\"}", "{}"));
        observer.await(1);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testFetchById() throws Exception {
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        UUID id = UUID.randomUUID();

        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        Optional<Fact> f = uut.fetchById(id);
        assertFalse(f.isPresent());
        uut.publish(Fact.of("{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                "{}"));
        f = uut.fetchById(id);
        assertTrue(f.isPresent());
        assertEquals(id, f.map(Fact::id).orElse(null));
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testAnySubscriptionsMatchesMark() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        UUID mark = uut.publishWithMark(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\""
                + UUID.randomUUID() + "\",\"type\":\"noone_knows\"}", "{}"));

        ArgumentCaptor<Fact> af = ArgumentCaptor.forClass(Fact.class);
        doNothing().when(observer).onNext(af.capture());

        uut.subscribeToFacts(SubscriptionRequest.catchup(ANY).fromScratch(), observer)
                .awaitComplete();

        verify(observer).onNext(any());
        assertEquals(mark, af.getValue().id());
        verify(observer).onComplete();
        verify(observer).onCatchup();
        verifyNoMoreInteractions(observer);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testRequiredMetaAttribute() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                "{}"));
        FactSpec REQ_FOO_BAR = FactSpec.ns("default").meta("foo", "bar");
        uut.subscribeToFacts(SubscriptionRequest.catchup(REQ_FOO_BAR).fromScratch(), observer)
                .awaitComplete();

        verify(observer).onNext(any());
        verify(observer).onCatchup();
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testScriptedWithPayloadFiltering() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                "{}"));
        FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript(
                "function (h,e){ return (h.hit=='me')}");
        uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                .awaitComplete();

        verify(observer).onNext(any());
        verify(observer).onCatchup();
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testScriptedWithHeaderFiltering() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                "{}"));
        FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript(
                "function (h){ return (h.hit=='me')}");
        uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                .awaitComplete();

        verify(observer).onNext(any());
        verify(observer).onCatchup();
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testScriptedFilteringMatchAll() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                "{}"));
        FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h){ return true }");
        uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                .awaitComplete();

        verify(observer, times(2)).onNext(any());
        verify(observer).onCatchup();
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testScriptedFilteringMatchNone() throws Exception {
        FactObserver observer = mock(FactObserver.class);
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}", "{}"));
        uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
                + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                "{}"));
        FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h){ return false }");
        uut.subscribeToFacts(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
                .awaitComplete();

        verify(observer).onCatchup();
        verify(observer).onComplete();
        verifyNoMoreInteractions(observer);
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testIncludeMarks() throws Exception {
        final UUID id = UUID.randomUUID();
        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).fromScratch(),
                observer).awaitComplete();

        verify(observer, times(2)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testSkipMarks() throws Exception {
        final UUID id = UUID.randomUUID();
        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));

        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default")).skipMarks()
                .fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testMatchBySingleAggId() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aggId1 = UUID.randomUUID();
        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggId\":[\"" + aggId1 + "\"]}",
                "{}"));

        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1))
                .skipMarks().fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testMatchByOneOfAggId() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aggId1 = UUID.randomUUID();
        final UUID aggId2 = UUID.randomUUID();

        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggId\":[\"" + aggId1 + "\",\""
                + aggId2 + "\"]}", "{}"));

        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1))
                .skipMarks().fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

        observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2))
                .skipMarks().fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testMatchBySecondAggId() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aggId1 = UUID.randomUUID();
        final UUID aggId2 = UUID.randomUUID();

        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggId\":[\"" + aggId1 + "\",\""
                + aggId2 + "\"]}", "{}"));

        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2))
                .skipMarks().fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

}
