package org.factcast.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class Fact0Test {

    @Test(expected = NullPointerException.class)
    public void testOfNull1() throws Exception {
        Fact.of(null, "");
    }

    @Test(expected = NullPointerException.class)
    public void testOfNull2() throws Exception {
        Fact.of("", null);
    }

    @Test(expected = NullPointerException.class)
    public void testOfNull() throws Exception {
        Fact.of(null, null);
    }

    @Test
    public void testOf() throws Exception {
        Test0Fact f = new Test0Fact();
        Fact f2 = Fact.of(f.jsonHeader(), f.jsonPayload());

        assertEquals(f.id(), f2.id());
    }

}
