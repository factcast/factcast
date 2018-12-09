package org.factcast.store.pgsql.internal;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

public class PGFactStreamTest {

    @InjectMocks
    PGFactStream uut;

    @Test
    public void testConnectNullParameter() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            uut.connect(null);
        });
    }

}
