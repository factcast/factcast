package org.factcast.core.subscription;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.factcast.core.MarkFact;
import org.factcast.core.spec.FactSpec;
import org.junit.Before;
import org.junit.Test;

public class SubscriptionRequestTO0Test {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testDebugInfo() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertEquals(r.debugInfo(), uut.debugInfo());
    }

    @Test
    public void testDumpContainsDebugInfo() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertTrue(uut.dump().contains(r.debugInfo()));
    }

    @Test
    public void testToString() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        String debugInfo = r.debugInfo();

        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertEquals(debugInfo, uut.toString());
    }

    @Test
    public void testSpecsContainMarkSpec() throws Exception {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertEquals(2, uut.specs().size());
        assertEquals(s, uut.specs().get(1));
        assertEquals(MarkFact.MARK_TYPE, uut.specs().get(0).type());

    }

    @Test
    public void testHasAnyScriptFilters() throws Exception {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertFalse(uut.hasAnyScriptFilters());

        uut.addSpecs(Arrays.asList(FactSpec.ns("buh").jsFilterScript(
                "function (h,e){ return true }")));

        assertTrue(uut.hasAnyScriptFilters());

    }

    @Test(expected = NullPointerException.class)
    public void testAddSpecsNull() throws Exception {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        uut.addSpecs(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddSpecsEmpty() throws Exception {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        uut.addSpecs(Collections.emptyList());
    }

    @Test
    public void testMaxDelay() throws Exception {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertEquals(0, uut.maxBatchDelayInMs());
        uut.maxBatchDelayInMs(7);
        assertEquals(7, uut.maxBatchDelayInMs());
    }

    @Test
    public void testAddSpecs() throws Exception {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertEquals(2, uut.specs().size());
        assertEquals(s, uut.specs().get(1));
        assertEquals(MarkFact.MARK_TYPE, uut.specs().get(0).type());

        final String js = "function (h,e){ return true }";
        uut.addSpecs(Arrays.asList(FactSpec.ns("buh").jsFilterScript(js)));

        assertEquals(3, uut.specs().size());
        assertEquals(js, uut.specs().get(2).jsFilterScript());

    }

    @Test
    public void testSkipMarks() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo"))
                .skipMarks()
                .fromScratch();

        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertFalse(uut.marks());
    }

    @Test
    public void testDontSkipMarks() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch();

        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertTrue(uut.marks());
    }

}
