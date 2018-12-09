package org.factcast.store.pgsql.internal;

import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicLong;

import org.factcast.store.pgsql.internal.query.PGLatestSerialFetcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

@ExtendWith(MockitoExtension.class)
public class PGSynchronizedQueryTest {

    PGSynchronizedQuery uut;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    JdbcTemplate jdbcTemplate;

    String sql = "SELECT 42";

    @Mock
    PreparedStatementSetter setter;

    @Mock
    RowCallbackHandler rowHandler;

    @Mock
    AtomicLong serialToContinueFrom;

    @Mock
    PGLatestSerialFetcher fetcher;

    @Test
    public void testRunWithIndex() throws Exception {
        uut = new PGSynchronizedQuery(jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom,
                fetcher);
        uut.run(true);
        verify(jdbcTemplate, never()).execute(startsWith("SET LOCAL enable_bitmapscan"));
    }

    @Test
    public void testRunWithoutIndex() throws Exception {
        uut = new PGSynchronizedQuery(jdbcTemplate, sql, setter, rowHandler, serialToContinueFrom,
                fetcher);
        uut.run(false);
        verify(jdbcTemplate).execute(startsWith("SET LOCAL enable_bitmapscan"));
    }
}
