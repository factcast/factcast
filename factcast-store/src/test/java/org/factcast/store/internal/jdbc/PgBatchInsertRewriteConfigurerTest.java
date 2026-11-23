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
package org.factcast.store.internal.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;

class PgBatchInsertRewriteConfigurerTest {
  @Test
  void leavesDelegatedAndJndiPoolsUntouched() {
    var delegated = org.mockito.Mockito.mock(org.apache.tomcat.jdbc.pool.DataSource.class);
    org.mockito.Mockito.when(delegated.getDataSource())
        .thenReturn(new org.postgresql.ds.PGSimpleDataSource());
    var jndi = org.mockito.Mockito.mock(org.apache.tomcat.jdbc.pool.DataSource.class);
    org.mockito.Mockito.when(jndi.getDataSourceJNDI()).thenReturn("jdbc/primary");
    var configurer = new PgBatchInsertRewriteConfigurer();

    assertThat(configurer.postProcessBeforeInitialization(delegated, "delegated"))
        .isSameAs(delegated);
    assertThat(configurer.postProcessBeforeInitialization(jndi, "jndi")).isSameAs(jndi);
    org.mockito.Mockito.verify(delegated, org.mockito.Mockito.never())
        .setUrl(org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.verify(jndi, org.mockito.Mockito.never())
        .setUrl(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void leavesUnconfiguredPoolAndUnrelatedBeansUntouched() {
    var ds = new org.apache.tomcat.jdbc.pool.DataSource();
    var bean = new Object();
    var configurer = new PgBatchInsertRewriteConfigurer();
    assertThat(configurer.postProcessBeforeInitialization(ds, "pool")).isSameAs(ds);
    assertThat(ds.getUrl()).isNull();
    assertThat(ds.getDbProperties()).doesNotContainKey("reWriteBatchedInserts");
    assertThat(configurer.postProcessBeforeInitialization(bean, "other")).isSameAs(bean);
  }

  @Test
  void enablesRewritingWhenNoDriverPropertiesWereConfigured() {
    var ds = org.mockito.Mockito.mock(org.apache.tomcat.jdbc.pool.DataSource.class);
    org.mockito.Mockito.when(ds.getUrl()).thenReturn("jdbc:postgresql://localhost/db");
    new PgBatchInsertRewriteConfigurer().postProcessBeforeInitialization(ds, "pool");
    var properties = org.mockito.ArgumentCaptor.forClass(java.util.Properties.class);
    org.mockito.Mockito.verify(ds).setDbProperties(properties.capture());
    assertThat(properties.getValue()).containsEntry("reWriteBatchedInserts", "true");
    org.mockito.Mockito.verify(ds).setUrl("jdbc:postgresql://localhost/db");
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties
  static class Config {
    @Bean
    static PgBatchInsertRewriteConfigurer configurer() {
      return new PgBatchInsertRewriteConfigurer();
    }

    @Bean
    @ConfigurationProperties("test.datasource")
    org.apache.tomcat.jdbc.pool.DataSource dataSource() {
      return org.mockito.Mockito.spy(new org.apache.tomcat.jdbc.pool.DataSource());
    }
  }

  @Test
  void enablesAfterPropertyBindingWithoutStartingThePool() throws Exception {
    try (var context = new AnnotationConfigApplicationContext()) {
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "test",
                  Map.of(
                      "test.datasource.url",
                          "jdbc:postgresql://localhost/db?sslmode=require&reWriteBatchedInserts=false",
                      "test.datasource.connection-properties",
                          "socketTimeout=42;reWriteBatchedInserts=false")));
      context.register(Config.class);
      context.refresh();
      var ds = context.getBean(org.apache.tomcat.jdbc.pool.DataSource.class);
      org.mockito.Mockito.verify(ds, org.mockito.Mockito.never()).getPool();
      org.mockito.Mockito.verify(ds, org.mockito.Mockito.never()).createPool();
      assertThat(ds.getPoolSize()).isZero();
      assertThat(ds.getUrl())
          .isEqualTo("jdbc:postgresql://localhost/db?sslmode=require&reWriteBatchedInserts=true");
      assertThat(ds.getDbProperties())
          .containsEntry("socketTimeout", "42")
          .containsEntry("reWriteBatchedInserts", "true");
    }
  }

  @Test
  void leavesOtherDriversUnchanged() {
    var ds = new org.apache.tomcat.jdbc.pool.DataSource();
    ds.setUrl("jdbc:h2:mem:test");
    new PgBatchInsertRewriteConfigurer().postProcessBeforeInitialization(ds, "dataSource");
    assertThat(ds.getDbProperties()).doesNotContainKey("reWriteBatchedInserts");
  }

  @Test
  void configuresDirectPostgresDataSource() {
    var ds = new org.postgresql.ds.PGSimpleDataSource();
    ds.setURL("jdbc:postgresql://localhost/db?reWriteBatchedInserts=false");
    new PgBatchInsertRewriteConfigurer().postProcessBeforeInitialization(ds, "dataSource");
    assertThat(ds.getReWriteBatchedInserts()).isTrue();
  }

  @Test
  void leavesAlreadyStartedPoolsAlone() {
    var ds = org.mockito.Mockito.mock(org.apache.tomcat.jdbc.pool.DataSource.class);
    org.mockito.Mockito.when(ds.getUrl()).thenReturn("jdbc:postgresql://localhost/db");
    org.mockito.Mockito.when(ds.getPoolSize()).thenReturn(1);
    new PgBatchInsertRewriteConfigurer().postProcessBeforeInitialization(ds, "dataSource");
    org.mockito.Mockito.verify(ds, org.mockito.Mockito.never())
        .setDbProperties(org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.verify(ds, org.mockito.Mockito.never())
        .setUrl(org.mockito.ArgumentMatchers.any());
  }
}
