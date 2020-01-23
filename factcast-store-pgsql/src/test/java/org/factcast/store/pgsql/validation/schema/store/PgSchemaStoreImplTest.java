package org.factcast.store.pgsql.validation.schema.store;

import org.factcast.store.pgsql.internal.PgTestConfiguration;
import org.factcast.store.pgsql.validation.schema.SchemaStore;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = { PgTestConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PgSchemaStoreImplTest extends AbstractSchemaStoreTest {

	@Autowired
	private JdbcTemplate tpl;

	@Override
	protected SchemaStore createUUT() {
		return new PgSchemaStoreImpl(tpl);
	}
}
