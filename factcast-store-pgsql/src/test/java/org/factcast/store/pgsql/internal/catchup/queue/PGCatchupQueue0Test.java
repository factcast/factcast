package org.factcast.store.pgsql.internal.catchup.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PGCatchupQueue0Test {

    private PGCatchupQueue uut = new PGCatchupQueue(71);

    @Test
    public void testInitialIsDone() {
        assertFalse(uut.isDone());
    }

    @Test
    public void testDone() {
        assertFalse(uut.isDone());
        uut.notifyDone();
        assertTrue(uut.isDone());
    }

    @Test
    public void testCapacity() {
        assertEquals(71, uut.remainingCapacity());
    }
}
