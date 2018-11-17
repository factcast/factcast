package org.factcast.store.pgsql.internal;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStore0Test;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = { PGEmbeddedConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PGFactStore3IT extends AbstractFactStore0Test {

    @Autowired
    FactStore store;

    @Override
    protected FactStore createStoreToTest() {
        return store;
    }
}
