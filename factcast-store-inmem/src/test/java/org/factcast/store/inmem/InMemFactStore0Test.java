package org.factcast.store.inmem;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ExecutorService;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStore0Test;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class InMemFactStore0Test extends AbstractFactStore0Test {

    private InMemFactStore store;

    @Override
    protected FactStore createStoreToTest() {
        this.store = new InMemFactStore();
        return store;
    }

    @Test
    public void testDestroy() throws Exception {
        ExecutorService es = mock(ExecutorService.class);
        InMemFactStore inMemFactStore = new InMemFactStore(es);
        inMemFactStore.shutdown();
        verify(es).shutdown();
    }
}
