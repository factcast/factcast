package org.factcast.store.pgsql.internal.listen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.factcast.store.pgsql.internal.metrics.PGMetricNames;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class PGConnectionTester0Test {
    @Mock
    private MetricRegistry registry;

    private PGConnectionTester uut;

    @Mock
    private PreparedStatement st;

    @Mock
    private ResultSet rs;

    @Mock
    private Counter counter;

    @Before
    public void setUp() {
        final String fail = new PGMetricNames().connectionFailure();
        when(registry.counter(fail)).thenReturn(counter);
        uut = new PGConnectionTester(registry);
    }

    @Test
    public void testTestPositive() throws Exception {

        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt(1)).thenReturn(42);

        boolean test = uut.test(c);
        assertTrue(test);
        verifyZeroInteractions(counter);
    }

    @Test
    public void testTestFailure() throws Exception {

        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt(1)).thenReturn(1);

        boolean test = uut.test(c);
        assertFalse(test);
        verify(counter).inc();
    }

    @Test
    public void testTestException1() throws Exception {

        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt(1)).thenThrow(new SQLException("BAM"));

        boolean test = uut.test(c);
        assertFalse(test);
        verify(counter).inc();
    }

    @Test
    public void testTestException2() throws Exception {

        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenReturn(rs);
        when(rs.next()).thenThrow(new SQLException("BAM"));

        boolean test = uut.test(c);
        assertFalse(test);
        verify(counter).inc();
    }

    @Test
    public void testTestException3() throws Exception {

        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenReturn(st);
        when(st.executeQuery()).thenThrow(new SQLException("BAM"));

        boolean test = uut.test(c);
        assertFalse(test);
        verify(counter).inc();
    }

    @Test
    public void testTestException4() throws Exception {

        Connection c = mock(Connection.class);
        when(c.prepareStatement(anyString())).thenThrow(new SQLException("BAM"));

        boolean test = uut.test(c);
        assertFalse(test);
        verify(counter).inc();
    }

    @Test(expected = NullPointerException.class)
    public void testPGConnectionTester() throws Exception {
        new PGConnectionTester(null);
    }

}
