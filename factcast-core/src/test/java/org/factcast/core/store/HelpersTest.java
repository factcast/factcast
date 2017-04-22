package org.factcast.core.store;

import static org.junit.Assert.*;

import java.util.List;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.wellknown.MarkFact;
import org.junit.Test;

public class HelpersTest {

	@Test(expected = NullPointerException.class)
	public void testToListFactNull() throws Exception {
		Helpers.toList(null);
	}

	@Test
	public void testToListFact() throws Exception {
		TestFact f = new TestFact();
		List<Fact> list = Helpers.toList(f);
		assertEquals(1, list.size());
		assertTrue(list.contains(f));
	}

	@Test
	public void testToListFactMarkFact() throws Exception {
		TestFact f = new TestFact();
		MarkFact m = new MarkFact();
		List<Fact> list = Helpers.toList(f, m);
		assertEquals(2, list.size());
		assertTrue(list.contains(f));
		assertTrue(list.contains(m));
		assertEquals(1, list.indexOf(m));
	}
}
