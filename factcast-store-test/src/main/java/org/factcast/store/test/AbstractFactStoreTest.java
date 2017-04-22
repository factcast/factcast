package org.factcast.store.test;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactObserver;
import org.factcast.core.subscription.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.annotation.DirtiesContext;

public abstract class AbstractFactStoreTest {

	private static final FactSpec ANY = FactSpec.ns("default");

	FactCast uut;

	@Before
	public void setUp() throws Exception {
		uut = FactCast.from(createStoreToTest());
	}

	protected abstract FactStore createStoreToTest();

	@Test
	@DirtiesContext
	public void testEmptyStore() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.subscribe(SubscriptionRequest.catchup(ANY).asFacts().sinceInception(), ido).get();
		Thread.sleep(200);// TODO
		verify(ido).onCatchup();
		verify(ido).onComplete();
		verify(ido, never()).onError(any());
		verify(ido, never()).onNext(any());
	}

	@Test
	@DirtiesContext
	public void testEmptyStoreFollowNonMatching() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.subscribe(SubscriptionRequest.follow(ANY).asFacts().sinceInception(), ido).get();
		Thread.sleep(200);// TODO
		verify(ido).onCatchup();
		verify(ido, never()).onComplete();
		verify(ido, never()).onError(any());
		verify(ido, never()).onNext(any());

		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"other\"}", "{}"));
		Thread.sleep(200);

		verify(ido, never()).onNext(any());
	}

	@Test
	@DirtiesContext
	public void testEmptyStoreFollowMatching() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.subscribe(SubscriptionRequest.follow(ANY).asFacts().sinceInception(), ido).get();
		Thread.sleep(200);// TODO
		verify(ido).onCatchup();
		verify(ido, never()).onComplete();
		verify(ido, never()).onError(any());
		verify(ido, never()).onNext(any());

		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
		Thread.sleep(200);

		verify(ido, times(1)).onNext(any());
	}

	@Test
	@DirtiesContext
	public void testEmptyStoreCatchupMatching() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
		uut.subscribe(SubscriptionRequest.catchup(ANY).asFacts().sinceInception(), ido).get();

		verify(ido).onCatchup();
		verify(ido).onComplete();
		verify(ido, never()).onError(any());
		verify(ido).onNext(any());
	}

	@Test
	@DirtiesContext
	public void testEmptyStoreFollowMatchingDelayed() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
		uut.subscribe(SubscriptionRequest.follow(ANY).asFacts().sinceInception(), ido).get();
		verify(ido).onCatchup();
		verify(ido, never()).onComplete();
		verify(ido, never()).onError(any());
		verify(ido).onNext(any());

		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
		Thread.sleep(200);
		verify(ido, times(2)).onNext(any());
	}

	@Test
	@DirtiesContext
	public void testEmptyStoreFollowNonmMatchingDelayed() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"type\":\"t1\"}", "{}"));
		uut.subscribe(SubscriptionRequest.follow(ANY).asFacts().sinceInception(), ido).get();
		verify(ido).onCatchup();
		verify(ido, never()).onComplete();
		verify(ido, never()).onError(any());
		verify(ido).onNext(any());

		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"other\",\"type\":\"t1\"}", "{}"));
		Thread.sleep(200);
		verify(ido, times(1)).onNext(any());
	}

	@Test
	@DirtiesContext
	public void testFetchById() throws Exception {
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
		UUID id = UUID.randomUUID();

		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
		Optional<Fact> f = uut.fetchById(id);
		assertFalse(f.isPresent());
		uut.publish(Fact.of("{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
		f = uut.fetchById(id);
		assertTrue(f.isPresent());
		assertEquals(id, f.map(Fact::id).get());
	}

	@Test
	@DirtiesContext
	public void testAnySubscriptionsMatchesMark() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		UUID mark = uut.publishWithMark(
				Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"no_idea\",\"type\":\"noone_knows\"}", "{}"));

		ArgumentCaptor<Fact> af = ArgumentCaptor.forClass(Fact.class);
		doNothing().when(ido).onNext(af.capture());

		uut.subscribe(SubscriptionRequest.catchup(ANY).asFacts().sinceInception(), ido).get();

		verify(ido).onNext(any());
		assertEquals(mark, af.getValue().id());
		verify(ido).onCatchup();
		verify(ido).onComplete();
		verifyNoMoreInteractions(ido);
	}

	@Test
	@DirtiesContext
	public void testRequiredMetaAttribute() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(
				Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"type\":\"noone_knows\"}", "{}"));
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
				+ "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}", "{}"));
		FactSpec REQ_FOO_BAR = FactSpec.ns("default").meta("foo", "bar");
		uut.subscribe(SubscriptionRequest.catchup(REQ_FOO_BAR).asFacts().sinceInception(), ido).get();

		verify(ido).onNext(any());
		verify(ido).onCatchup();
		verify(ido).onComplete();
		verifyNoMoreInteractions(ido);
	}

	@Test
	@DirtiesContext
	public void testScriptedWithPayloadFiltering() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(Fact.of(
				"{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
				"{}"));
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
				+ "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}", "{}"));
		FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h,e){ return (h.hit=='me')}");
		uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).asFacts().sinceInception(), ido).get();

		verify(ido).onNext(any());
		verify(ido).onCatchup();
		verify(ido).onComplete();
		verifyNoMoreInteractions(ido);
	}

	@Test
	@DirtiesContext
	public void testScriptedWithHeaderFiltering() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(Fact.of(
				"{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
				"{}"));
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
				+ "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}", "{}"));
		FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h){ return (h.hit=='me')}");
		uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).asFacts().sinceInception(), ido).get();

		verify(ido).onNext(any());
		verify(ido).onCatchup();
		verify(ido).onComplete();
		verifyNoMoreInteractions(ido);
	}

	@Test
	@DirtiesContext
	public void testScriptedFilteringMatchAll() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(Fact.of(
				"{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
				"{}"));
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
				+ "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}", "{}"));
		FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h){ return true }");
		uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).asFacts().sinceInception(), ido).get();

		verify(ido, times(2)).onNext(any());
		verify(ido).onCatchup();
		verify(ido).onComplete();
		verifyNoMoreInteractions(ido);
	}

	@Test
	@DirtiesContext
	public void testScriptedFilteringMatchNone() throws Exception {
		FactObserver ido = mock(FactObserver.class);
		uut.publish(Fact.of(
				"{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
				"{}"));
		uut.publish(Fact.of("{\"id\":\"" + UUID.randomUUID()
				+ "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}", "{}"));
		FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h){ return false }");
		uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).asFacts().sinceInception(), ido).get();

		verify(ido).onCatchup();
		verify(ido).onComplete();
		verifyNoMoreInteractions(ido);
	}

}
