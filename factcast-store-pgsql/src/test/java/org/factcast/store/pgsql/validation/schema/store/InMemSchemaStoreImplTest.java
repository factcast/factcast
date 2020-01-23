package org.factcast.store.pgsql.validation.schema.store;

import org.factcast.store.pgsql.validation.schema.SchemaStore;

public class InMemSchemaStoreImplTest extends AbstractSchemaStoreTest {

	@Override
	protected SchemaStore createUUT() {
		return new InMemSchemaStoreImpl();
	}
}
