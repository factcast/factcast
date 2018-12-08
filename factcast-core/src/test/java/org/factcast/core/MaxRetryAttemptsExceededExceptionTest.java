package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class MaxRetryAttemptsExceededExceptionTest {

    @Test
    public void testMaxRetryAttemptsExceededExceptionNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new MaxRetryAttemptsExceededException(null);
        });
    }

}
