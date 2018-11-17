package org.factcast.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
public class FactCast0Test {

    @Captor
    ArgumentCaptor<List<Fact>> facts;

    @Test
    public void testFrom() {
        FactStore store = mock(FactStore.class);
        FactCast fc = FactCast.from(store);
        assertTrue(fc instanceof DefaultFactCast);
    }

    @Test
    public void testFromNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactCast.from(null);
        });
    }

    @Test
    public void testFromReadOnlyNull() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            FactCast.fromReadOnly(null);
        });
    }

    @Test
    public void testFromReadOnly() {
        FactStore store = mock(FactStore.class);
        ReadFactCast fc = FactCast.fromReadOnly(store);
        assertTrue(fc instanceof DefaultFactCast);
    }

    @Test
    public void testPublishWithMarkOne() {
        FactStore store = mock(FactStore.class);
        doNothing().when(store).publish(facts.capture());
        final Test0Fact f = new Test0Fact();
        FactCast.from(store).publishWithMark(f);
        List<Fact> published = facts.getValue();
        assertEquals(2, published.size());
        assertSame(f, published.get(0));
        assertTrue(FactSpecMatcher.matches(FactSpec.forMark()).test(published.get(1)));
    }

    @Test
    public void testPublishWithMarkMany() {
        FactStore store = mock(FactStore.class);
        doNothing().when(store).publish(facts.capture());
        final Test0Fact f = new Test0Fact();
        FactCast.from(store).publishWithMark(Collections.singletonList(f));
        List<Fact> published = facts.getValue();
        assertEquals(2, published.size());
        assertSame(f, published.get(0));
        assertTrue(FactSpecMatcher.matches(FactSpec.forMark()).test(published.get(1)));
    }
}
