package org.factcast.core.store;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class RetryableExceptionTest {

    @Test
    public void testRetryableExceptionNullContract() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new RetryableException(null);
        });
    }
}
