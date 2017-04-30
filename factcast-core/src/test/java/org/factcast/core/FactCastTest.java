package org.factcast.core;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.factcast.core.store.FactStore;
import org.junit.Test;

public class FactCastTest {

    @Test
    public void testFrom() throws Exception {
        FactStore store = mock(FactStore.class);
        FactCast fc = FactCast.from(store);

        assertTrue(fc instanceof DefaultFactCast);

    }

    @Test(expected = NullPointerException.class)
    public void testFromNull() throws Exception {
        FactCast.from(null);
    }

    @Test
    public void testFromReadOnly() throws Exception {
        FactStore store = mock(FactStore.class);
        ReadFactCast fc = FactCast.fromReadOnly(store);

        assertTrue(fc instanceof DefaultFactCast);

    }

}
