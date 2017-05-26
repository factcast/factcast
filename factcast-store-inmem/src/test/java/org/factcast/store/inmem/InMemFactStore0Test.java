package org.factcast.store.inmem;

import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutorService;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStore0Test;
import org.junit.Test;

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
