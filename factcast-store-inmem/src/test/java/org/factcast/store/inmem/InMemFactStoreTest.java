package org.factcast.store.inmem;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.concurrent.ExecutorService;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStoreTest;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class InMemFactStoreTest extends AbstractFactStoreTest {

    private InMemFactStore store;

    @Override
    protected FactStore createStoreToTest() {
        this.store = new InMemFactStore();
        return store;
    }

    @Test
    void testDestroy() throws Exception {
        ExecutorService es = mock(ExecutorService.class);
        InMemFactStore inMemFactStore = new InMemFactStore(es);
        inMemFactStore.shutdown();
        verify(es).shutdown();
    }

    @Test
    public void testInMemFactStoreExecutorServiceNullConstructor() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            new InMemFactStore(null);
        });
    }

}
