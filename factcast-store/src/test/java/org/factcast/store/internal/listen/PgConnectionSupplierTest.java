/*
 * Copyright © 2017-2020 factcast.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.store.internal.listen;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.*;

import com.google.common.base.Splitter;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import lombok.*;
import org.apache.tomcat.jdbc.pool.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.jdbc.PgConnection;

@ExtendWith(MockitoExtension.class)
class PgConnectionSupplierTest {

  PgConnectionSupplier uut;

  @Mock private org.apache.tomcat.jdbc.pool.DataSource ds;

  @BeforeEach
  void setUp() {
    uut = new PgConnectionSupplier(ds);
  }

  private void setupConnectionProperties(String properties) {
    final var singleConnectionProperties =
        properties != null
            ? Splitter.on(";").omitEmptyStrings().withKeyValueSeparator("=").split(properties)
            : Map.of();

    PoolConfiguration poolConf = mock(PoolConfiguration.class);
    final var props = new Properties();
    props.putAll(singleConnectionProperties);
    when(poolConf.getDbProperties()).thenReturn(props);
    when(ds.getPoolProperties()).thenReturn(poolConf);
  }

  @Test
  void test_wrongDataSourceImplementation() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          DataSource ds = mock(DataSource.class);
          new PgConnectionSupplier(ds);
          failBecauseExceptionWasNotThrown(IllegalStateException.class);
        });
  }

  @Test
  void test_buildCredentials() {
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
  void test_socketTimeoutIsOverriddenBy0() {
    // given
    setupConnectionProperties("socketTimeout=30;connectTimeout=20;loginTimeout=10;");
    // when
    Properties connectionProperties = uut.buildPgConnectionProperties(ds);
    // then
    assertEquals("0", connectionProperties.get("socketTimeout"));
  }

  @Test
  void test_noPredefinedSocketTimeout() {
    // given
    setupConnectionProperties("connectTimeout=20;loginTimeout=10;");
    // when
    Properties connectionProperties = uut.buildPgConnectionProperties(ds);
    // then
    assertEquals("0", connectionProperties.get("socketTimeout"));
  }

  @Test
  void test_connectionTimeout() {
    // given
    setupConnectionProperties("socketTimeout=30;connectTimeout=20;loginTimeout=10;");
    // when
    Properties connectionProperties = uut.buildPgConnectionProperties(ds);
    // then
    assertEquals("20", connectionProperties.get("connectTimeout"));
  }

  @Test
  void test_loginTimeout() {
    // given
    setupConnectionProperties("socketTimeout=30;connectTimeout=20;loginTimeout=10;");
    // when
    Properties connectionProperties = uut.buildPgConnectionProperties(ds);
    // then
    assertEquals("10", connectionProperties.get("loginTimeout"));
  }

  @Test
  void test_noRelevantPropertySet() {
    setupConnectionProperties("sockettimeout=30");
    uut.buildPgConnectionProperties(ds);
  }

  @Test
  void test_propertySyntaxBroken() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          setupConnectionProperties("sockettimeout:30");
          uut.buildPgConnectionProperties(ds);
        });
  }

  @Test
  void test_nullProperties() {
    setupConnectionProperties(null);
    uut.buildPgConnectionProperties(ds);
  }

  @Test
  void test_invalidConnectionProperties() {
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          // given
          setupConnectionProperties("socketTimeout=30;connectTimeout=20,loginTimeout=10;");
          // when
          uut.buildPgConnectionProperties(ds);
          failBecauseExceptionWasNotThrown(IllegalArgumentException.class);
        });
  }

  @Test
  void test_constructor() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new PgConnectionSupplier(null);
          failBecauseExceptionWasNotThrown(NullPointerException.class);
        });
  }

  @Test
  void testExceptionOnDriverManager_getConnection() {
    String url = "jdbc:xyz:foo";
    org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
    ds.setUrl(url);
    PgConnectionSupplier uut = new PgConnectionSupplier(ds);
    assertThatThrownBy(() -> uut.getUnpooledConnection("")).isInstanceOf(SQLException.class);
  }

  @Test
  void testTomcatDataSourceIsUsed() {
    org.apache.tomcat.jdbc.pool.DataSource ds = new org.apache.tomcat.jdbc.pool.DataSource();
    PgConnectionSupplier uut = new PgConnectionSupplier(ds);
    assertThat(uut.ds).isSameAs(ds);
  }

  @Test
  @SneakyThrows
  void getConnectionClientIdProvided() {
    final var driver = mock(Driver.class);
    final var connection = mock(Connection.class);
    final var pgConnection = mock(PgConnection.class);
    final var propertiesCaptor = ArgumentCaptor.forClass(Properties.class);

    when(driver.connect(anyString(), any(Properties.class))).thenReturn(connection);
    when(connection.unwrap(any())).thenReturn(pgConnection);
    when(ds.getUrl()).thenReturn("hugo");

    try (var driverManager = mockStatic(DriverManager.class)) {

      driverManager.when(() -> DriverManager.getDriver(anyString())).thenReturn(driver);

      assertThat(uut.getUnpooledConnection("client-id")).isSameAs(pgConnection);

      driverManager.verify(() -> DriverManager.getDriver(ds.getUrl()));
    }

    verify(connection).unwrap(PgConnection.class);
    verify(driver).connect(eq("hugo"), propertiesCaptor.capture());
    final var actualProperties = propertiesCaptor.getValue();
    assertThat(actualProperties.getProperty("ApplicationName")).isEqualTo("factcast|client-id");
  }

  @Test
  @SneakyThrows
  void getConnectionApplicationNameConfigured() {
    final var driver = mock(Driver.class);
    final var connection = mock(Connection.class);
    final var pgConnection = mock(PgConnection.class);
    final var propertiesCaptor = ArgumentCaptor.forClass(Properties.class);
    final var poolProps = mock(PoolConfiguration.class);
    final var dbProps = new Properties();
    dbProps.setProperty("ApplicationName", "foo");

    when(driver.connect(anyString(), any(Properties.class))).thenReturn(connection);
    when(connection.unwrap(any())).thenReturn(pgConnection);
    when(ds.getUrl()).thenReturn("hugo");
    when(ds.getPoolProperties()).thenReturn(poolProps);
    when(poolProps.getDbProperties()).thenReturn(dbProps);

    final var underTest = new PgConnectionSupplier(ds);

    try (var driverManager = mockStatic(DriverManager.class)) {

      driverManager.when(() -> DriverManager.getDriver(anyString())).thenReturn(driver);

      assertThat(underTest.getUnpooledConnection("client-id")).isSameAs(pgConnection);

      driverManager.verify(() -> DriverManager.getDriver(ds.getUrl()));
    }

    verify(connection).unwrap(PgConnection.class);
    verify(driver).connect(eq("hugo"), propertiesCaptor.capture());
    final var actualProperties = propertiesCaptor.getValue();
    assertThat(actualProperties.getProperty("ApplicationName")).isEqualTo("foo|client-id");
  }

  @Test
  @SneakyThrows
  void pooledConnection() {
    Connection c = mock(Connection.class);
    when(ds.getConnection()).thenReturn(c);
    PreparedStatement ps = mock(PreparedStatement.class);
    when(c.prepareStatement("SET application_name='factcast|poit'")).thenReturn(ps);

    Connection poit = uut.getPooledConnection("poit");
    assertThat(poit).isNotNull().isSameAs(c);
    verify(ps).execute();
  }

  @Nested
  class ModifiedSingleConnectionDataSourceTest {
    @Mock Connection c;
    @Mock ConnectionModifier cm1;
    @Mock ConnectionModifier cm2;

    @Test
    void cleansUpConnection() {
      var uut = new PgConnectionSupplier.ModifiedSingleConnectionDataSource(c, List.of(cm1));
      uut.close();

      verify(cm1).beforeReturn(c);
    }

    @Test
    void reversesOrder() {
      InOrder inOrder = inOrder(cm1, cm2);
      var uut = new PgConnectionSupplier.ModifiedSingleConnectionDataSource(c, List.of(cm1, cm2));
      inOrder.verify(cm1).afterBorrow(c);
      inOrder.verify(cm2).afterBorrow(c);

      uut.close();

      inOrder.verify(cm2).beforeReturn(c);
      inOrder.verify(cm1).beforeReturn(c);
    }
  }
}
