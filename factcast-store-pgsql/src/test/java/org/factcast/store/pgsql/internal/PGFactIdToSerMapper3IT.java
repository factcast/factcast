package org.factcast.store.pgsql.internal;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.UUID;

import org.factcast.core.MarkFact;
import org.factcast.core.store.FactStore;
import org.factcast.store.pgsql.internal.query.PGFactIdToSerialMapper;
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
public class PGFactIdToSerMapper3IT {

    @Autowired
    JdbcTemplate tpl;

    @Autowired
    FactStore store;

    @Test
    public void testRetrieve() {
        MarkFact m = new MarkFact();
        store.publish(Collections.singletonList(m));
        long retrieve = new PGFactIdToSerialMapper(tpl).retrieve(m.id());
        assertTrue(retrieve > 0);
    }

    @Test
    public void testRetrieveNonExistant() {
        try {
            new PGFactIdToSerialMapper(tpl).retrieve(UUID.fromString(
                    "2b86d90e-2755-4f82-b86d-fd092b25ccc8"));
            fail();
        } catch (Throwable ignored) {
        }
    }
}
