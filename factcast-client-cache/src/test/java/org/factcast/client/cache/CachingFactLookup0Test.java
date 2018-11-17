package org.factcast.client.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CachingFactLookup0Test {

    private CachingFactLookup uut;

    private FactStore store;

    @BeforeEach
    public void setUp() {
        store = mock(FactStore.class);
        uut = new CachingFactLookup(store);
    }

    @Test
    public void testLookupFails() {
        when(store.fetchById(any())).thenReturn(Optional.empty());
        final UUID id = UUID.randomUUID();
        Optional<Fact> lookup = uut.lookup(id);
        assertFalse(lookup.isPresent());
        verify(store).fetchById(id);
    }

    @Test
    public void testLookupWorks() {
        final Fact f = Fact.builder().ns("test").build("{}");
        when(store.fetchById(f.id())).thenReturn(Optional.of(f));
        Optional<Fact> lookup = uut.lookup(f.id());
        assertTrue(lookup.isPresent());
        assertEquals(f, lookup.get());
        verify(store).fetchById(f.id());
    }

    @Test
    public void testConstructorNullParam() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new CachingFactLookup(null);
        });
    }
}
