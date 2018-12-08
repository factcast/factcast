package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.List;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecMatcher;
import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FactCastTest {

    @Captor
    ArgumentCaptor<List<Fact>> facts;

    @Test
    void testFrom() {
        FactStore store = mock(FactStore.class);
        FactCast fc = FactCast.from(store);
        assertTrue(fc instanceof DefaultFactCast);
    }

    @Test
    void testFromNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactCast.from(null);
        });
    }

    @Test
    void testFromReadOnlyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactCast.fromReadOnly(null);
        });
    }

    @Test
    void testFromReadOnly() {
        FactStore store = mock(FactStore.class);
        ReadFactCast fc = FactCast.fromReadOnly(store);
        assertTrue(fc instanceof DefaultFactCast);
    }

    @Test
    void testPublishWithMarkOne() {
        FactStore store = mock(FactStore.class);
        doNothing().when(store).publish(facts.capture());
        final TestFact f = new TestFact();
        FactCast.from(store).publishWithMark(f);
        List<Fact> published = facts.getValue();
        assertEquals(2, published.size());
        assertSame(f, published.get(0));
        assertTrue(FactSpecMatcher.matches(FactSpec.forMark()).test(published.get(1)));
    }

    @Test
    void testPublishWithMarkMany() {
        FactStore store = mock(FactStore.class);
        doNothing().when(store).publish(facts.capture());
        final TestFact f = new TestFact();
        FactCast.from(store).publishWithMark(Collections.singletonList(f));
        List<Fact> published = facts.getValue();
        assertEquals(2, published.size());
        assertSame(f, published.get(0));
        assertTrue(FactSpecMatcher.matches(FactSpec.forMark()).test(published.get(1)));
    }

    @Test
    void testRetryValidatesMaxAttempts() {
        FactStore store = mock(FactStore.class);
        assertThrows(IllegalArgumentException.class, () -> FactCast.from(store).retry(-42));
    }

    @Test
    public void testRetryChecksNumberOfAttempts() throws Exception {
        FactStore store = mock(FactStore.class);
        FactCast fc = FactCast.from(store);
        assertThrows(IllegalArgumentException.class, () -> {
            fc.retry(0);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            fc.retry(0, 100);
        });

        assertNotNull(fc.retry(10));

    }

    @Test
    public void testRetryChecksWaitInterval() throws Exception {

        FactStore store = mock(FactStore.class);
        FactCast fc = FactCast.from(store);

        assertThrows(IllegalArgumentException.class, () -> {
            fc.retry(10, -100);
        });

        assertNotNull(fc.retry(10, 0));
        assertNotNull(fc.retry(10, 1));
        assertNotNull(fc.retry(10, 100));

    }
}
