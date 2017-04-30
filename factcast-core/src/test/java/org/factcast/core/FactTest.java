package org.factcast.core;

import static org.junit.Assert.*;

import org.junit.Test;

public class FactTest {

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
        TestFact f = new TestFact();
        Fact f2 = Fact.of(f.jsonHeader(), f.jsonPayload());

        assertEquals(f.id(), f2.id());
    }

}
