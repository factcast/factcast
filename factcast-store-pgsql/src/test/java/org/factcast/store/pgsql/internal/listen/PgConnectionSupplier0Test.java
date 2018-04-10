package org.factcast.store.pgsql.internal.listen;

import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.*;

import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

@SuppressWarnings("all")
public class PgConnectionSupplier0Test {

    PgConnectionSupplier uut;

    @Mock
    private org.apache.tomcat.jdbc.pool.DataSource ds;

    @Before
    public void prepare() {

        initMocks(this);
        uut = new PgConnectionSupplier(ds);
    }

    @Test(expected = IllegalStateException.class)
    public void test_wrongDataSourceImplementation() {

        DataSource ds = mock(DataSource.class);
        new PgConnectionSupplier(ds);

        failBecauseExceptionWasNotThrown(IllegalStateException.class);
    }

    @Test
    public void test_buildCredentials() {

        // given
        PoolConfiguration poolConf = mock(PoolConfiguration.class);
        when(poolConf.getPassword()).thenReturn("testPassword");
        when(poolConf.getUsername()).thenReturn("testUsername");
        when(ds.getPoolProperties()).thenReturn(poolConf);

        // when
        Properties creds = uut.buildCredentialProperties(ds);

        // then
        assertEquals("testUsername", creds.get("user"));
        assertEquals("testPassword", creds.get("password"));
    }

    @Test(expected = NullPointerException.class)
    public void test_constructor() {

        new PgConnectionSupplier(null);

        failBecauseExceptionWasNotThrown(NullPointerException.class);

    }

    @Test
    public void testExceptionOnDriverManager_getConnection() throws Exception {
        String url = "jdbc:xyz:foo";
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.setUrl(url);

        PgConnectionSupplier uut = new PgConnectionSupplier(ds);

        assertThatThrownBy(() -> uut.get()).isInstanceOf(SQLException.class);
    }
}