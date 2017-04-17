package org.factcast.store.inmem;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStoreTest;

public class InMemFactStoreTest extends AbstractFactStoreTest {

	@Override
	protected FactStore createStoreToTest() {
		return new InMemFactStore();
	}

}
