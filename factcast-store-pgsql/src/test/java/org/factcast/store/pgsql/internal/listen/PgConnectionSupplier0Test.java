package org.factcast.store.pgsql.internal.listen;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.apache.tomcat.jdbc.pool.PoolConfiguration;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class PgConnectionSupplier0Test {

    PgConnectionSupplier uut;

    @Mock
    private org.apache.tomcat.jdbc.pool.DataSource ds;

    @BeforeEach
    public void prepare() {
        initMocks(this);
        uut = new PgConnectionSupplier(ds);
    }

    private void setupConnectionProperties(String properties) {
        PoolConfiguration poolConf = mock(PoolConfiguration.class);
        when(poolConf.getConnectionProperties()).thenReturn(properties);
        when(ds.getPoolProperties()).thenReturn(poolConf);
    }

    @Test
    public void test_wrongDataSourceImplementation() {
        Assertions.assertThrows(IllegalStateException.class, () -> {
            DataSource ds = mock(DataSource.class);
            new PgConnectionSupplier(ds);
            failBecauseExceptionWasNotThrown(IllegalStateException.class);
        });
    }

    @Test
    public void test_buildCredentials() {
        // given
        PoolConfiguration poolConf = mock(PoolConfiguration.class);
        when(poolConf.getPassword()).thenReturn("testPassword");
        when(poolConf.getUsername()).thenReturn("testUsername");
        when(ds.getPoolProperties()).thenReturn(poolConf);
        // when
        Properties creds = uut.buildPgConnectionProperties(ds);
        // then
        assertEquals("testUsername", creds.get("user"));
        assertEquals("testPassword", creds.get("password"));
    }

    @Test
    public void test_socketTimeout() {
        // given
        setupConnectionProperties("socketTimeout=30;connectTimeout=20;loginTimeout=10;");
        // when
        Properties connectionProperties = uut.buildPgConnectionProperties(ds);
        // then
        assertEquals("30", connectionProperties.get("socketTimeout"));
    }

    @Test
    public void test_connectionTimeout() {
        // given
        setupConnectionProperties("socketTimeout=30;connectTimeout=20;loginTimeout=10;");
        // when
        Properties connectionProperties = uut.buildPgConnectionProperties(ds);
        // then
        assertEquals("20", connectionProperties.get("connectTimeout"));
    }

    @Test
    public void test_loginTimeout() {
        // given
        setupConnectionProperties("socketTimeout=30;connectTimeout=20;loginTimeout=10;");
        // when
        Properties connectionProperties = uut.buildPgConnectionProperties(ds);
        // then
        assertEquals("10", connectionProperties.get("loginTimeout"));
    }

    @Test
    public void test_noRelevantPropertySet() {
        setupConnectionProperties("sockettimeout=30");
        uut.buildPgConnectionProperties(ds);
    }

    @Test
    public void test_propertySyntaxBroken() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            setupConnectionProperties("sockettimeout:30");
            uut.buildPgConnectionProperties(ds);
        });
    }

    @Test
    public void test_nullProperties() {
        setupConnectionProperties(null);
        uut.buildPgConnectionProperties(ds);
    }

    @Test
    public void test_invalidConnectionProperties() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> {
            // given
            setupConnectionProperties("socketTimeout=30;connectTimeout=20,loginTimeout=10;");
            // when
            uut.buildPgConnectionProperties(ds);
            failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        });
    }

    @Test
    public void test_constructor() {
        Assertions.assertThrows(NullPointerException.class, () -> {
            new PgConnectionSupplier(null);
            failBecauseExceptionWasNotThrown(NullPointerException.class);
        });
    }

    @Test
    public void testExceptionOnDriverManager_getConnection() {
        String url = "jdbc:xyz:foo";
        org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
        ds.setUrl(url);
        PgConnectionSupplier uut = new PgConnectionSupplier(ds);
        assertThatThrownBy(uut::get).isInstanceOf(SQLException.class);
    }
}
