/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.factcast.store.internal.PgFactStoreInternalConfiguration.P1_CATCHUP_DATASOURCE_BEAN_NAME;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.P1CatchupDataSourceProperties;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {
      PgTestConfiguration.class,
      PgFactStoreP1OffloadIntegrationTest.P1DataSourceInstrumentationConfig.class
    })
@ContextConfiguration(
    initializers =
        PgFactStoreP1OffloadIntegrationTest.P1CatchupDataSourcePropertiesInitializer.class)
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
class PgFactStoreP1OffloadIntegrationTest {

  static final String NS = "p1-offload";

  @Autowired FactStore store;

  @Autowired BeanFactory beanFactory;

  @Autowired DataSource primaryDataSource;

  @Test
  void phase1UsesSeparateDataSourceAndPhase2CatchesUpOnPrimary() throws Exception {
    CountingDataSource p1CatchupDataSource =
        beanFactory.getBean(P1_CATCHUP_DATASOURCE_BEAN_NAME, CountingDataSource.class);

    store.publish(
        List.of(
            Fact.builder().ns(NS).type("test").buildWithoutPayload(),
            Fact.builder().ns(NS).type("test").buildWithoutPayload()));

    p1CatchupDataSource.reset();
    CountingObserver observer = new CountingObserver();

    CompletableFuture<Void> subscription =
        CompletableFuture.runAsync(
            () ->
                store
                    .subscribe(
                        SubscriptionRequestTO.from(
                            SubscriptionRequest.catchup(FactSpec.ns(NS)).fromScratch()),
                        observer)
                    .awaitComplete());

    p1CatchupDataSource.awaitPhase1QueryStarted();

    store.publish(List.of(Fact.builder().ns(NS).type("test").buildWithoutPayload()));
    p1CatchupDataSource.releasePhase1Query();

    subscription.get(10, TimeUnit.SECONDS);

    assertThat(observer.facts()).hasValue(3);
    assertThat(observer.catchups()).hasValue(1);
    assertThat(observer.completes()).hasValue(1);
    assertThat(observer.error()).hasNullValue();
    assertThat(p1CatchupDataSource.target()).isNotSameAs(primaryDataSource);
    assertThat(p1CatchupDataSource.connections()).hasPositiveValue();
  }

  static class P1CatchupDataSourcePropertiesInitializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(@NonNull ConfigurableApplicationContext applicationContext) {
      try {
        Class.forName(PgTestConfiguration.class.getName());
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException(e);
      }

      Map<String, Object> properties = new LinkedHashMap<>();
      putIfPresent(properties, "driver-class-name", "spring.datasource.driver-class-name");
      putIfPresent(properties, "url", "spring.datasource.url");
      putIfPresent(properties, "username", "spring.datasource.username");
      putIfPresent(properties, "password", "spring.datasource.password");
      properties.put(
          P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".type",
          org.apache.tomcat.jdbc.pool.DataSource.class.getName());
      properties.put(P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".max-active", "4");
      properties.put(P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".initial-size", "0");
      properties.put(P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".min-idle", "0");
      properties.put(P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".max-idle", "2");
      properties.put(P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".test-on-borrow", "true");
      properties.put(
          P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".validation-query", "select 1");
      properties.put(
          P1CatchupDataSourceProperties.PROPERTIES_PREFIX + ".connection-properties",
          "socketTimeout=0;preparedStatementCacheSize=0;");
      applicationContext
          .getEnvironment()
          .getPropertySources()
          .addFirst(new MapPropertySource("p1CatchupDataSource", properties));
    }

    private void putIfPresent(
        Map<String, Object> properties, String p1PropertyName, String primaryPropertyName) {
      String value = System.getProperty(primaryPropertyName);
      if (value != null) {
        properties.put(
            P1CatchupDataSourceProperties.PROPERTIES_PREFIX + "." + p1PropertyName, value);
      }
    }
  }

  @Configuration
  static class P1DataSourceInstrumentationConfig {

    @Bean
    static BeanPostProcessor p1CatchupDataSourceInstrumenter() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(Object bean, String beanName)
            throws BeansException {
          if (P1_CATCHUP_DATASOURCE_BEAN_NAME.equals(beanName)
              && bean instanceof DataSource dataSource
              && !(bean instanceof CountingDataSource)) {
            return new CountingDataSource(dataSource);
          }
          return bean;
        }
      };
    }
  }

  static class CountingDataSource extends DelegatingDataSource {

    private final DataSource target;
    private final AtomicInteger connections = new AtomicInteger();
    private final AtomicReference<Throwable> queryInstrumentationFailure = new AtomicReference<>();
    private final CountDownLatch phase1QueryStarted = new CountDownLatch(1);
    private final CountDownLatch releasePhase1Query = new CountDownLatch(1);
    private final AtomicBoolean holdNextCatchupQuery = new AtomicBoolean();

    CountingDataSource(DataSource dataSource) {
      super(dataSource);
      target = dataSource;
    }

    @Override
    public @NonNull Connection getConnection() throws SQLException {
      connections.incrementAndGet();
      return instrument(super.getConnection());
    }

    @Override
    public @NonNull Connection getConnection(@NonNull String username, @NonNull String password)
        throws SQLException {
      connections.incrementAndGet();
      return instrument(super.getConnection(username, password));
    }

    private Connection instrument(Connection connection) {
      return (Connection)
          Proxy.newProxyInstance(
              connection.getClass().getClassLoader(),
              new Class<?>[] {Connection.class},
              (proxy, method, args) -> {
                try {
                  Object result = method.invoke(connection, args);
                  if ("prepareStatement".equals(method.getName())
                      && result instanceof PreparedStatement statement
                      && args != null
                      && args.length > 0
                      && args[0] instanceof String sql
                      && isCatchupQuery(sql)) {
                    return instrument(statement);
                  }
                  return result;
                } catch (InvocationTargetException e) {
                  throw e.getTargetException();
                }
              });
    }

    private PreparedStatement instrument(PreparedStatement statement) {
      return (PreparedStatement)
          Proxy.newProxyInstance(
              statement.getClass().getClassLoader(),
              new Class<?>[] {PreparedStatement.class},
              (proxy, method, args) -> {
                try {
                  Object result = method.invoke(statement, args);
                  if ("executeQuery".equals(method.getName())
                      && holdNextCatchupQuery.compareAndSet(true, false)) {
                    phase1QueryStarted.countDown();
                    releasePhase1Query.await(10, TimeUnit.SECONDS);
                  }
                  return result;
                } catch (InvocationTargetException e) {
                  throw e.getTargetException();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  queryInstrumentationFailure.set(e);
                  throw new SQLException("Interrupted while holding phase 1 query", e);
                }
              });
    }

    private boolean isCatchupQuery(String sql) {
      String normalized = sql.toLowerCase();
      return normalized.contains(" from fact ")
          && normalized.contains(" where ")
          && normalized.contains(" order by ser asc");
    }

    void awaitPhase1QueryStarted() throws InterruptedException {
      assertThat(phase1QueryStarted.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(queryInstrumentationFailure).hasNullValue();
    }

    void releasePhase1Query() {
      releasePhase1Query.countDown();
    }

    void close() {
      if (target instanceof org.apache.tomcat.jdbc.pool.DataSource tomcatDataSource) {
        tomcatDataSource.close();
      }
    }

    void reset() {
      connections.set(0);
      queryInstrumentationFailure.set(null);
      holdNextCatchupQuery.set(true);
    }

    DataSource target() {
      return target;
    }

    AtomicInteger connections() {
      return connections;
    }
  }

  static class CountingObserver implements FactObserver {

    private final AtomicInteger facts = new AtomicInteger();
    private final AtomicInteger catchups = new AtomicInteger();
    private final AtomicInteger completes = new AtomicInteger();
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    @Override
    public void onNext(@NonNull Fact element) {
      facts.incrementAndGet();
    }

    @Override
    public void onCatchup() {
      catchups.incrementAndGet();
    }

    @Override
    public void onComplete() {
      completes.incrementAndGet();
    }

    @Override
    public void onError(@NonNull Throwable exception) {
      error.set(exception);
    }

    AtomicInteger facts() {
      return facts;
    }

    AtomicInteger catchups() {
      return catchups;
    }

    AtomicInteger completes() {
      return completes;
    }

    AtomicReference<Throwable> error() {
      return error;
    }
  }
}
