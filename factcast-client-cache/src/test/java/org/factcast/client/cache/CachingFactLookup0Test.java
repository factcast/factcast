package org.factcast.client.cache;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.Test0Fact;
import org.factcast.core.store.FactStore;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CachingFactLookup0Test {

    private CachingFactLookup uut;

    private FactStore store;

    @Before
    public void setUp() throws Exception {
        store = mock(FactStore.class);
        uut = new CachingFactLookup(store);
    }

    @Test
    public void testLookupFails() throws Exception {
        when(store.fetchById(any())).thenReturn(Optional.empty());

        final UUID id = UUID.randomUUID();
        Optional<Fact> lookup = uut.lookup(id);

        assertFalse(lookup.isPresent());
        verify(store).fetchById(id);
    }

    @Test
    public void testLookupWorks() throws Exception {
        final Test0Fact f = new Test0Fact();
        when(store.fetchById(f.id())).thenReturn(Optional.of(f));

        Optional<Fact> lookup = uut.lookup(f.id());

        assertTrue(lookup.isPresent());
        assertEquals(f, lookup.get());

        verify(store).fetchById(f.id());
    }

    @Test(expected = NullPointerException.class)
    public void testConstructorNullParam() throws Exception {
        new CachingFactLookup(null);
    }

}
