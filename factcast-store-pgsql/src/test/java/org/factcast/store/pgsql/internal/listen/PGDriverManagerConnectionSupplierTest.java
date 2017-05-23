package org.factcast.store.pgsql.internal.listen;

import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.Properties;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class PGDriverManagerConnectionSupplierTest {

    PGDriverManagerConnectionSupplier uut;

    @Mock
    private org.apache.tomcat.jdbc.pool.DataSource ds;

    @Before
    public void prepare() {

        initMocks(this);
        uut = new PGDriverManagerConnectionSupplier(ds);
    }

    @SuppressWarnings("unused")
    @Test(expected = IllegalStateException.class)
    public void test_wrongDataSourceImplementation() {

        DataSource ds = mock(DataSource.class);
        new PGDriverManagerConnectionSupplier(ds);

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

    @SuppressWarnings("unused")
    @Test(expected = NullPointerException.class)
    public void test_constructor() {

        new PGDriverManagerConnectionSupplier(null);

        failBecauseExceptionWasNotThrown(NullPointerException.class);

    }
}