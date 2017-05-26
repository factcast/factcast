package org.factcast.store.pgsql.internal;

import org.factcast.core.store.FactStore;
import org.factcast.store.test.AbstractFactStore0Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PGEmbeddedConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
public class PGFactStore3IT extends AbstractFactStore0Test {

    @Autowired
    FactStore store;

    @Override
    protected FactStore createStoreToTest() {
        return store;
    }

}
