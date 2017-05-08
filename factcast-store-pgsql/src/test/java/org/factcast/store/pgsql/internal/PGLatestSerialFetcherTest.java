package org.factcast.store.pgsql.internal;

import static org.junit.Assert.*;

import java.util.UUID;

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
public class PGLatestSerialFetcherTest {
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PGLatestSerialFetcher uut;

    @Test
    public void testRetrieveLatestSer() throws Exception {

        uut = new PGLatestSerialFetcher(jdbcTemplate);

        assertEquals(0, uut.retrieveLatestSer());
        assertEquals(0, uut.retrieveLatestSer());

        jdbcTemplate.execute("INSERT INTO " + PGConstants.TABLE_FACT + "("
                + PGConstants.COLUMN_HEADER + "," + PGConstants.COLUMN_PAYLOAD
                + ") VALUES('{\"id\":\"" + UUID.randomUUID() + "\"}','{}') ");

        assertEquals(1, uut.retrieveLatestSer());

        jdbcTemplate.execute("INSERT INTO " + PGConstants.TABLE_FACT + "("
                + PGConstants.COLUMN_HEADER + "," + PGConstants.COLUMN_PAYLOAD
                + ") VALUES('{\"id\":\"" + UUID.randomUUID() + "\"}','{}') ");

        jdbcTemplate.execute("INSERT INTO " + PGConstants.TABLE_FACT + "("
                + PGConstants.COLUMN_HEADER + "," + PGConstants.COLUMN_PAYLOAD
                + ") VALUES('{\"id\":\"" + UUID.randomUUID() + "\"}','{}') ");

        assertEquals(3, uut.retrieveLatestSer());

    }

}
