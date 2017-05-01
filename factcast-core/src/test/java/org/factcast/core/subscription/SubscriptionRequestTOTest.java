package org.factcast.core.subscription;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.factcast.core.spec.FactSpec;
import org.junit.Before;
import org.junit.Test;

public class SubscriptionRequestTOTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testDebugInfo() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        String debugInfo = r.debugInfo();

        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);

        assertEquals(r.debugInfo(), uut.debugInfo());
    }

    @Test
    public void testDumpContainsDebugInfo() throws Exception {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        String debugInfo = r.debugInfo();

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
        assertEquals(FactSpec.forMark(), uut.specs().get(0));

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

}
