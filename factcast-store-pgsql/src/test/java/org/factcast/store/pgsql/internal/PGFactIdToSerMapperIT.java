package org.factcast.store.pgsql.internal;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.UUID;

import org.factcast.core.MarkFact;
import org.factcast.core.store.FactStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = { PGEmbeddedConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
public class PGFactIdToSerMapperIT {

    @Autowired
    JdbcTemplate tpl;

    @Autowired
    FactStore store;

    @Test
    public void testRetrieve() throws Exception {
        MarkFact m = new MarkFact();
        store.publish(Arrays.asList(m));
        long retrieve = new PGFactIdToSerMapper(tpl).retrieve(m.id());
        assertTrue(retrieve > 0);
    }

    @Test
    public void testRetrieveNonExistant() throws Exception {
        try {
            new PGFactIdToSerMapper(tpl).retrieve(UUID.fromString(
                    "2b86d90e-2755-4f82-b86d-fd092b25ccc8"));
            fail();
        } catch (Throwable e) {
        }
    }
}
