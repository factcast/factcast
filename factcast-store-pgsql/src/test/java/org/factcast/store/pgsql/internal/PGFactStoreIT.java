package org.factcast.store.pgsql.internal;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStoreTest;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PGEmbeddedConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
public class PGFactStoreIT extends AbstractFactStoreTest {

    @Autowired
    FactStore store;

    @Override
    protected FactStore createStoreToTest() {
        return store;
    }

}
