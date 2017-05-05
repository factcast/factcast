package org.factcast.store.pgsql.internal;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.stubbing.defaultanswers.ReturnsDeepStubs;
import org.mockito.runners.MockitoJUnitRunner;

import com.codahale.metrics.MetricRegistry;

//FIXME can be a junit test
@RunWith(MockitoJUnitRunner.class)
public class ConnectionTesterTest {

    @Mock
    Connection conn;

    @Mock
    PreparedStatement ps;

    @Mock
    ResultSet rs;

    @Test(expected = NullPointerException.class)
    public void testConstructor() throws Exception {
        new ConnectionTester(null);
    }

    @Test
    public void testTestPositive() throws Exception {
        final MetricRegistry mr = mock(MetricRegistry.class, new ReturnsDeepStubs());

        ConnectionTester tester = new ConnectionTester(mr);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.getInt(1)).thenReturn(42);

        assertTrue(tester.test(conn));

        verify(rs).next();
        verify(rs).getInt(1);
    }

    @Test
    public void testTestFailingExceptionally() throws Exception {
        final MetricRegistry mr = mock(MetricRegistry.class, new ReturnsDeepStubs());

        ConnectionTester tester = new ConnectionTester(mr);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.getInt(1)).thenThrow(new SQLException("b0rken"));

        assertFalse(tester.test(conn));

        verify(rs).next();
        verify(rs).getInt(1);
    }

    @Test
    public void testTestFailingWrongResult() throws Exception {
        final MetricRegistry mr = mock(MetricRegistry.class, new ReturnsDeepStubs());

        ConnectionTester tester = new ConnectionTester(mr);

        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.getInt(1)).thenReturn(-1);

        assertFalse(tester.test(conn));

        verify(rs).next();
        verify(rs).getInt(1);
    }
}
