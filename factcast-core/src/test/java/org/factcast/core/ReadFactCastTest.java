package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.Test;

public class ReadFactCastTest {

    @Test
    void testRetryValidatesMaxAttempts() {
        FactStore store = mock(FactStore.class);
        assertThrows(IllegalArgumentException.class, () -> FactCast.fromReadOnly(store).retry(-42));
    }

}
