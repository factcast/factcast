package org.factcast.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.List;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.spec.FactSpecMatcher;
import org.factcast.core.store.FactStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class FactCast0Test {

    @Captor
    ArgumentCaptor<List<Fact>> facts;

    @Test
    public void testFrom() throws Exception {
        FactStore store = mock(FactStore.class);
        FactCast fc = FactCast.from(store);

        assertTrue(fc instanceof DefaultFactCast);

    }

    @Test(expected = NullPointerException.class)
    public void testFromNull() throws Exception {
        FactCast.from(null);
    }

    @Test(expected = NullPointerException.class)
    public void testFromReadOnlyNull() throws Exception {
        FactCast.fromReadOnly(null);
    }

    @Test
    public void testFromReadOnly() throws Exception {
        FactStore store = mock(FactStore.class);
        ReadFactCast fc = FactCast.fromReadOnly(store);

        assertTrue(fc instanceof DefaultFactCast);

    }

    @Test
    public void testPublishWithMarkOne() throws Exception {
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
    public void testPublishWithMarkMany() throws Exception {
        FactStore store = mock(FactStore.class);
        doNothing().when(store).publish(facts.capture());

        final Test0Fact f = new Test0Fact();
        FactCast.from(store).publishWithMark(Arrays.asList(f));

        List<Fact> published = facts.getValue();

        assertEquals(2, published.size());
        assertSame(f, published.get(0));
        assertTrue(FactSpecMatcher.matches(FactSpec.forMark()).test(published.get(1)));

    }

}
