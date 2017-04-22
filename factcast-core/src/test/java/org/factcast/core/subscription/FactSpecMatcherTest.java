package org.factcast.core.subscription;

import static org.junit.Assert.*;

import java.util.UUID;

import org.factcast.core.TestFact;
import org.factcast.core.subscription.FactSpec;
import org.factcast.core.subscription.FactSpecMatcher;
import org.junit.Before;
import org.junit.Test;

public class FactSpecMatcherTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testMetaMatch() throws Exception {
		assertTrue(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new TestFact().meta("foo", "bar")));
		assertTrue(
				metaMatch(FactSpec.ns("default").meta("foo", "bar"), new TestFact().meta("x", "y").meta("foo", "bar")));
		assertTrue(metaMatch(FactSpec.ns("default"), new TestFact().meta("x", "y").meta("foo", "bar")));
		assertFalse(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new TestFact().meta("foo", "baz")));
		assertFalse(metaMatch(FactSpec.ns("default").meta("foo", "bar"), new TestFact()));

	}

	@Test
	public void testNsMatch() throws Exception {
		assertTrue(nsMatch(FactSpec.ns("default"), new TestFact().ns("default")));
		assertFalse(nsMatch(FactSpec.ns("default"), new TestFact().ns("xxx")));

	}

	@Test
	public void testTypeMatch() throws Exception {
		assertTrue(typeMatch(FactSpec.ns("default").type("a"), new TestFact().type("a")));
		assertTrue(typeMatch(FactSpec.ns("default"), new TestFact().type("a")));
		assertFalse(typeMatch(FactSpec.ns("default").type("a"), new TestFact().type("x")));
		assertFalse(typeMatch(FactSpec.ns("default").type("a"), new TestFact()));
	}

	@Test
	public void testAggIdMatch() throws Exception {

		UUID u1 = UUID.randomUUID();
		UUID u2 = UUID.randomUUID();

		assertTrue(aggIdMatch(FactSpec.ns("default").aggId(u1), new TestFact().aggId(u1)));
		assertTrue(aggIdMatch(FactSpec.ns("default"), new TestFact().aggId(u1)));
		assertFalse(aggIdMatch(FactSpec.ns("default").aggId(u1), new TestFact().aggId(u2)));
		assertFalse(aggIdMatch(FactSpec.ns("default").aggId(u1), new TestFact()));
	}

	@Test
	public void testScriptMatch() throws Exception {
		assertTrue(scriptMatch(FactSpec.ns("default"), new TestFact()));
		assertFalse(
				scriptMatch(FactSpec.ns("default").jsFilterScript("function (h,e){ return false }"), new TestFact()));
		assertTrue(scriptMatch(FactSpec.ns("default").jsFilterScript("function (h,e){ return h.meta.x=='y' }"),
				new TestFact().meta("x", "y")));
	}

	// ---------------------------

	private boolean nsMatch(FactSpec s, TestFact f) {
		return new FactSpecMatcher(s).nsMatch(f);
	}

	private boolean typeMatch(FactSpec s, TestFact f) {
		return new FactSpecMatcher(s).typeMatch(f);
	}

	private boolean aggIdMatch(FactSpec s, TestFact f) {
		return new FactSpecMatcher(s).aggIdMatch(f);
	}

	private boolean scriptMatch(FactSpec s, TestFact f) {
		return new FactSpecMatcher(s).scriptMatch(f);
	}

	private boolean metaMatch(FactSpec s, TestFact f) {
		return new FactSpecMatcher(s).metaMatch(f);
	}

}
