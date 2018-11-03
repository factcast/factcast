package org.factcast.store.inmem;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStore0Test;
import org.junit.Test;

import java.util.concurrent.ExecutorService;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@SuppressWarnings("deprecation")
public class InMemFactStore0Test extends AbstractFactStore0Test {

    @Override
    protected FactStore createStoreToTest() {
        return new InMemFactStore();
    }

    @Test
    public void testDestroy() throws Exception {
        ExecutorService es = mock(ExecutorService.class);
        InMemFactStore inMemFactStore = new InMemFactStore(es);

        inMemFactStore.destroy();
        verify(es).shutdown();
    }
}
