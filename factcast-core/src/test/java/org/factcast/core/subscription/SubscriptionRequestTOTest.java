package org.factcast.core.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.factcast.core.MarkFact;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SubscriptionRequestTOTest {

    @Test
    void testDebugInfo() {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertEquals(r.debugInfo(), uut.debugInfo());
    }

    @Test
    void testDumpContainsDebugInfo() {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertTrue(uut.dump().contains(r.debugInfo()));
    }

    @Test
    void testToString() {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.forMark()).fromScratch();
        String debugInfo = r.debugInfo();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertEquals(debugInfo, uut.toString());
    }

    @Test
    void testSpecsContainMarkSpec() {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertEquals(2, uut.specs().size());
        assertEquals(s, uut.specs().get(1));
        assertEquals(MarkFact.MARK_TYPE, uut.specs().get(0).type());
    }

    @Test
    void testHasAnyScriptFilters() {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertFalse(uut.hasAnyScriptFilters());
        uut.addSpecs(Collections.singletonList(FactSpec.ns("buh").jsFilterScript(
                "function (h,e){ return true }")));
        assertTrue(uut.hasAnyScriptFilters());
    }

    @Test
    void testAddSpecsNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            final FactSpec s = FactSpec.ns("foo");
            SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
            SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
            uut.addSpecs(null);
        });
    }

    @Test
    void testAddSpecsEmpty() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            final FactSpec s = FactSpec.ns("foo");
            SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
            SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
            uut.addSpecs(Collections.emptyList());
        });
    }

    @Test
    void testMaxDelay() {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertEquals(0, uut.maxBatchDelayInMs());
        uut.maxBatchDelayInMs(7);
        assertEquals(7, uut.maxBatchDelayInMs());
    }

    @Test
    void testAddSpecs() {
        final FactSpec s = FactSpec.ns("foo");
        SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertEquals(2, uut.specs().size());
        assertEquals(s, uut.specs().get(1));
        assertEquals(MarkFact.MARK_TYPE, uut.specs().get(0).type());
        final String js = "function (h,e){ return true }";
        uut.addSpecs(Collections.singletonList(FactSpec.ns("buh").jsFilterScript(js)));
        assertEquals(3, uut.specs().size());
        assertEquals(js, uut.specs().get(2).jsFilterScript());
    }

    @Test
    void testSkipMarks() {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo"))
                .skipMarks()
                .fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertFalse(uut.marks());
    }

    @Test
    void testDontSkipMarks() {
        SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch();
        SubscriptionRequestTO uut = SubscriptionRequestTO.forFacts(r);
        assertTrue(uut.marks());
    }
}
