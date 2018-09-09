/**
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
package org.factcast.store.test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

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

public abstract class AbstractFactStore0Test {

    static final FactSpec ANY = FactSpec.ns("default");

    protected FactCast uut;

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

        private List<Fact> values = new CopyOnWriteArrayList<>();

        @Override
        public void onNext(Fact element) {
            values.add(element);
        }

        @SneakyThrows
        public void await(int count) {

            while (true) {
                if (values.size() >= count) {
                    return;
                } else {
                    Thread.sleep(50);
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
        FactSpec SCRIPTED = FactSpec.ns("default")
                .jsFilterScript(
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
        FactSpec SCRIPTED = FactSpec.ns("default")
                .jsFilterScript(
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
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default"))
                .skipMarks()
                .fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testMatchBySingleAggId() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aggId1 = UUID.randomUUID();
        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + aggId1 + "\"]}",
                "{}"));

        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1))
                .skipMarks()
                .fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testMatchByOneOfAggId() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aggId1 = UUID.randomUUID();
        final UUID aggId2 = UUID.randomUUID();

        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + aggId1 + "\",\""
                + aggId2 + "\"]}", "{}"));

        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1))
                .skipMarks()
                .fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

        observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2))
                .skipMarks()
                .fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testMatchBySecondAggId() throws Exception {
        final UUID id = UUID.randomUUID();
        final UUID aggId1 = UUID.randomUUID();
        final UUID aggId2 = UUID.randomUUID();

        uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + aggId1 + "\",\""
                + aggId2 + "\"]}", "{}"));

        FactObserver observer = mock(FactObserver.class);
        uut.subscribeToFacts(SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2))
                .skipMarks()
                .fromScratch(), observer).awaitComplete();

        verify(observer, times(1)).onNext(any());

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testDelayed() throws Exception {
        final UUID id = UUID.randomUUID();

        TestFactObserver obs = new TestFactObserver();

        try (Subscription s = uut.subscribeToFacts(SubscriptionRequest.follow(500, FactSpec.ns(
                "default").aggId(id)).skipMarks().fromScratch(), obs);) {

            uut.publishWithMark(Fact.of("{\"id\":\"" + id
                    + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id + "\"]}",
                    "{}"));

            // will take some time on pgstore
            obs.await(1);
        }
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testSerialOf() throws Exception {

        final UUID id = UUID.randomUUID();

        assertFalse(uut.serialOf(id).isPresent());

        UUID mark1 = uut.publishWithMark(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id + "\"]}",
                "{}"));

        assertTrue(uut.serialOf(mark1).isPresent());
        assertTrue(uut.serialOf(id).isPresent());

        long serMark = uut.serialOf(mark1).getAsLong();
        long serFact = uut.serialOf(id).getAsLong();

        assertTrue(serFact < serMark);

    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testSerialHeader() throws Exception {

        UUID id = UUID.randomUUID();
        uut.publish(Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id + "\"]}",
                "{}"));

        UUID id2 = UUID.randomUUID();
        uut.publish(Fact.of("{\"id\":\"" + id2
                + "\",\"type\":\"someType\",\"meta\":{\"foo\":\"bar\"},\"ns\":\"default\",\"aggIds\":[\""
                + id2 + "\"]}",
                "{}"));

        OptionalLong serialOf = uut.serialOf(id);
        assertTrue(serialOf.isPresent());

        Fact f = uut.fetchById(id).get();
        Fact fact2 = uut.fetchById(id2).get();

        assertEquals(serialOf.getAsLong(), f.serial());
        assertTrue(f.before(fact2));
    }

    @Test(timeout = 10000)
    @DirtiesContext
    public void testUniqueIdentConstraintInLog() throws Exception {

        String ident = UUID.randomUUID().toString();

        UUID id = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Fact f1 = Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id
                + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}",
                "{}");
        Fact f2 = Fact.of("{\"id\":\"" + id2
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id2
                + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}",
                "{}");
        uut.publish(f1);

        // needs to fail due to uniqueIdentitfier not being unique
        try {
            uut.publish(f2);
            fail("Expected IllegalArgumentException due to unique_identifier being used a sencond time");
        } catch (IllegalArgumentException e) {

            // make sure, f1 was stored before
            assertTrue(uut.fetchById(id).isPresent());
        }
    }

    @Test(timeout=10000)
    @DirtiesContext
    public void testUniqueIdentConstraintInBatch() throws Exception {

        String ident = UUID.randomUUID().toString();
        
        UUID id = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        Fact f1 = Fact.of("{\"id\":\"" + id
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id
                + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}",
                "{}");
        Fact f2 = Fact.of("{\"id\":\"" + id2
                + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id2
                + "\"],\"meta\":{\"unique_identifier\":\"" + ident + "\"}}",
                "{}");

        // needs to fail due to uniqueIdentitfier not being unique
        try {
            uut.publish(Arrays.asList(f1, f2));
            fail("Expected IllegalArgumentException due to unique_identifier being used twice in a batch");
        } catch (IllegalArgumentException e) {

            // make sure, f1 was not stored either
            assertFalse(uut.fetchById(id).isPresent());
        }
    }

}
