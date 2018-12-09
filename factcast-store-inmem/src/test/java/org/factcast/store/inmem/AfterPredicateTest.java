package org.factcast.store.inmem;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.store.inmem.InMemFactStore.AfterPredicate;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class AfterPredicateTest {

    AfterPredicate uut = new AfterPredicate(new UUID(0L, 3L));

    @Test
    void testStatefulFiltering() throws Exception {

        assertFalse(uut.test(Fact.builder().id(new UUID(0L, 1)).build("{}")));
        assertFalse(uut.test(Fact.builder().id(new UUID(0L, 2)).build("{}")));
        assertFalse(uut.test(Fact.builder().id(new UUID(0L, 3)).build("{}")));
        assertTrue(uut.test(Fact.builder().id(new UUID(0L, 4)).build("{}")));
        // now that the flip is switched, anything should go through
        assertTrue(uut.test(Fact.builder().id(new UUID(0L, 1)).build("{}")));

    }
}
