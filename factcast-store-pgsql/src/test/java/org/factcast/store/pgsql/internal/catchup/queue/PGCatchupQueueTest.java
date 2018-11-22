package org.factcast.store.pgsql.internal.catchup.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

public class PGCatchupQueueTest {

    private PGCatchupQueue uut = new PGCatchupQueue(71);

    @Test
    void testInitialIsDone() {
        assertFalse(uut.isDone());
    }

    @Test
    void testDone() {
        assertFalse(uut.isDone());
        uut.notifyDone();
        assertTrue(uut.isDone());
    }

    @Test
    void testCapacity() {
        assertEquals(71, uut.remainingCapacity());
    }
}
