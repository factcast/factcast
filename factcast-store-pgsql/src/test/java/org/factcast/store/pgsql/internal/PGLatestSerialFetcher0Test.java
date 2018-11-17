package org.factcast.store.pgsql.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.factcast.store.pgsql.internal.query.PGLatestSerialFetcher;
import org.factcast.store.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(classes = { PGEmbeddedConfiguration.class })
@Sql(scripts = "/test_schema.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
public class PGLatestSerialFetcher0Test {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private PGLatestSerialFetcher uut;

    @Test
    public void testRetrieveLatestSer() {
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

    @Test
    public void testRetrieveLatestSerWithException() {
        JdbcTemplate jdbcMock = mock(JdbcTemplate.class);
        when(jdbcMock.queryForRowSet(anyString())).thenThrow(new EmptyResultDataAccessException(1));
        uut = new PGLatestSerialFetcher(jdbcMock);
        assertEquals(0, uut.retrieveLatestSer());
    }
}
