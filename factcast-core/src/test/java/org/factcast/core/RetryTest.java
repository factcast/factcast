package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyListOf;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import javax.sound.sampled.UnsupportedAudioFileException;

import org.factcast.core.store.FactStore;
import org.factcast.core.store.RetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import edu.umd.cs.findbugs.annotations.ExpectWarning;

@ExtendWith(MockitoExtension.class)
public class RetryTest {

    @Mock
    FactStore fs;

    @Test
    void testHappyPath() throws Exception {
        // arrange

        // Note: we intended the retryableException to be passed from store to factcast,
        // so we mock the store here

        doThrow(new RetryableException(new IllegalStateException()))//
                .doThrow(new RetryableException(new IllegalArgumentException()))//
                .doNothing()//
                .when(fs)
                .publish(anyListOf(Fact.class));

        // retry(5) wraps the factcast instance
        FactCast uut = FactCast.from(fs).retry(5);

        // act
        uut.publish(Fact.builder().build("{}"));
        verify(fs, times(3)).publish(anyListOf(Fact.class));
        verifyNoMoreInteractions(fs);
    }

    @Test
    void testWrapsOnlyOnce() throws Exception {
        FactCast uut = FactCast.from(fs).retry(3);
        FactCast doubleWrapped = uut.retry(5);

        assertSame(doubleWrapped, uut);
    }

    @Test
    void testMaxRetries() throws Exception {
        int maxRetries = 3;
        // as we literally "re"-try, we expect the original attempt plus maxRetries:
        int expectedPublishAttempts = maxRetries + 1;
        doThrow(new RetryableException(new RuntimeException(""))).when(fs).publish(anyListOf(
                Fact.class));
        FactCast uut = FactCast.from(fs).retry(maxRetries);

        assertThrows(MaxRetryAttemptsExceededException.class, () -> {
            uut.publish(Fact.builder().ns("foo").build("{}"));
        });

        verify(fs, times(expectedPublishAttempts)).publish(anyListOf(Fact.class));
        verifyNoMoreInteractions(fs);
    }

    @Test
    public void testWrapIllegalArguments() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            FactCast.from(fs).retry(3, -1);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            FactCast.from(fs).retry(0, 10);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            FactCast.from(fs).retry(-2, 10);
        });
        assertNotNull(FactCast.from(fs).retry(1, 0));
    }

    @Test
    void testThrowNonRetryableException() throws Exception {
        int maxRetries = 3;
        doThrow(new UnsupportedOperationException("not retryable")).when(fs).publish(anyListOf(
                Fact.class));
        FactCast uut = FactCast.from(fs).retry(maxRetries);

        assertThrows(UnsupportedOperationException.class, () -> {
            uut.publish(Fact.builder().ns("foo").build("{}"));
        });

        verify(fs, times(1)).publish(anyListOf(Fact.class));
        verifyNoMoreInteractions(fs);
    }

}
