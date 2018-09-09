package org.factcast.store.inmem;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStore0Test;
import org.junit.Test;

@SuppressWarnings("deprecation")
public class InMemFactStore0Test extends AbstractFactStore0Test {

    private InMemFactStore store;

    @Override
    protected FactStore createStoreToTest() {
        this.store=new InMemFactStore();
        return store;
    }

    @Test
    public void testDestroy() throws Exception {
        ExecutorService es = mock(ExecutorService.class);
        InMemFactStore inMemFactStore = new InMemFactStore(es);

        inMemFactStore.destroy();
        verify(es).shutdown();
    }
    
    @Test
    public void testClear() throws Exception {
        Fact f1 = DefaultFact.of("{\"id\":\"" + UUID.randomUUID().toString() + "\"}", "{}");
        assertEquals(0, store.store.size());

        uut.publish(Arrays.asList(f1));
        assertEquals(1, store.store.size());

        store.clear();
        assertEquals(0, store.store.size());
    }

}
