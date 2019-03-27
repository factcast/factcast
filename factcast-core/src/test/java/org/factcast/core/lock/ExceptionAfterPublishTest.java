package org.factcast.core.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class ExceptionAfterPublishTest {

    @Test
    public void testNullContracts() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new ExceptionAfterPublish(null, new RuntimeException());
        });
        assertThrows(NullPointerException.class, () -> {
            new ExceptionAfterPublish(UUID.randomUUID(), null);
        });
        assertThrows(NullPointerException.class, () -> {
            new ExceptionAfterPublish(null, null);
        });

    }

    @Test
    public void testExceptionAfterPublish() throws Exception {
        Throwable e = Mockito.mock(Exception.class);
        UUID id = UUID.randomUUID();
        ExceptionAfterPublish uut = new ExceptionAfterPublish(id, e);
        assertThat(uut.lastFactId()).isSameAs(id);
        assertThat(uut.getCause()).isSameAs(e);

    }

}
