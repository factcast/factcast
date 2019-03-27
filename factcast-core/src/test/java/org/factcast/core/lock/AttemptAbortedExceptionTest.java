package org.factcast.core.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class AttemptAbortedExceptionTest {
    @Test
    public void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new AttemptAbortedException((String) null);
        });
        assertThrows(NullPointerException.class, () -> {
            new AttemptAbortedException((Exception) null);
        });

        assertThat(new AttemptAbortedException("foo").getMessage()).isEqualTo("foo");
        Exception e = Mockito.mock(Exception.class);
        assertThat(new AttemptAbortedException(e).getCause()).isSameAs(e);
    }

}
