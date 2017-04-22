package org.factcast.core.subscription;

import static org.junit.Assert.*;

import org.factcast.core.wellknown.MarkFact;
import org.junit.Test;

public class FactSpecTest {

	@Test
	public void testMarkMatcher() throws Exception {
		assertTrue(new FactSpecMatcher(FactSpec.forMark()).test(new MarkFact()));
	}

}
