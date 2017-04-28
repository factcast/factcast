package org.factcast.core.spec;

import static org.junit.Assert.*;

import org.factcast.core.MarkFact;
import org.junit.Test;

//TODO remove?
public class FactSpecTest {

    @Test
    public void testMarkMatcher() throws Exception {
        assertTrue(new FactSpecMatcher(FactSpec.forMark()).test(new MarkFact()));
    }

}
