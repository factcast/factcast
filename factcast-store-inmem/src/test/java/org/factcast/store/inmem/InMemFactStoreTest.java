package org.factcast.store.inmem;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.UUID;

import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class InMemFactStoreTest {

    InMemFactStore uut = new InMemFactStore();

    @Test
    public void testClear() throws Exception {
        Fact f1 = DefaultFact.of("{\"id\":\"" + UUID.randomUUID().toString() + "\"}", "{}");
        assertEquals(0, uut.store.size());

        uut.publish(Arrays.asList(f1));
        assertEquals(1, uut.store.size());

        uut.clear();
        assertEquals(0, uut.store.size());
    }

}
