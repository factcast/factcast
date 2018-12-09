package org.factcast.store.pgsql.internal.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
public class PGLatestSerialFetcherTest {
    @InjectMocks
    PGLatestSerialFetcher uut;

    @Mock
    JdbcTemplate jdbc;

    @Test
    public void testRetrieveLatestSerRetuns0WhenExceptionThrown() throws Exception {
        when(jdbc.queryForRowSet(anyString())).thenThrow(UnsupportedOperationException.class);
        assertEquals(0, uut.retrieveLatestSer());
    }

}
