package org.factcast.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public class HelpersTest {

    @Test(expected = NullPointerException.class)
    public void testToListFactNull() throws Exception {
        Helpers.toList(null);
    }

    @Test(expected = NullPointerException.class)
    public void testToListFactMarkNull() throws Exception {
        Helpers.toList((Fact) null, new MarkFact());
    }

    @Test(expected = NullPointerException.class)
    public void testToListFactMark2Null() throws Exception {
        Helpers.toList(new TestFact(), null);
    }

    @Test(expected = NullPointerException.class)
    public void testToListFactMarkManyNull() throws Exception {
        Helpers.toList((List<Fact>) null, new MarkFact());
    }

    @Test(expected = NullPointerException.class)
    public void testToListFactMarkMany2Null() throws Exception {
        Helpers.toList(Arrays.asList(new TestFact()), null);
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
