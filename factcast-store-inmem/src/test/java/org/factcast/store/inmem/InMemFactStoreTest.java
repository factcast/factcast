package org.factcast.store.inmem;

import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutorService;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStoreTest;
import org.junit.Test;

public class InMemFactStoreTest extends AbstractFactStoreTest {

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
