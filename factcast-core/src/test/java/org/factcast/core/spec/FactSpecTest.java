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

    @Test(expected = NullPointerException.class)
    public void testMetaBothNull() throws Exception {
        FactSpec.ns("foo").meta(null, null);
    }

    @Test(expected = NullPointerException.class)
    public void testMetaKeyNull() throws Exception {
        FactSpec.ns("foo").meta(null, "");
    }

    @Test(expected = NullPointerException.class)
    public void testMetaValueNull() throws Exception {
        FactSpec.ns("foo").meta("", null);
    }

    @Test(expected = NullPointerException.class)
    public void testFactSpecConstructorNull() throws Exception {
        new FactSpec(null);
    }
}
