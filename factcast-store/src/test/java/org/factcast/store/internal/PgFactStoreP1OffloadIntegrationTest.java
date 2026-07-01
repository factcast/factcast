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
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.internal.catchup.PgCatchup;
import org.factcast.store.internal.catchup.PgCatchupFactory;
import org.factcast.store.internal.pipeline.ServerPipeline;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(
    classes = {
      PgTestConfiguration.class,
      PgFactStoreP1OffloadIntegrationTest.P1OffloadTestConfig.class
    })
@TestPropertySource("/p1-catchup-datasource.properties")
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@IntegrationTest
class PgFactStoreP1OffloadIntegrationTest {

  static final String NS = "p1-offload";
  private static final String PG_CATCHUP_FACTORY_BEAN_NAME = "pgCatchupFactory";

  // @TestPropertySource resolves placeholders before Spring instantiates PgTestConfiguration,
  // so trigger its static Testcontainers datasource setup before loading the P1 datasource file.
  static {
    try {
      Class.forName(PgTestConfiguration.class.getName());
    } catch (ClassNotFoundException e) {
      throw new IllegalStateException(e);
    }
  }

  @Autowired FactStore store;

  @Autowired BeanFactory beanFactory;

  @Autowired DataSource primaryDataSource;

  @Test
  @SneakyThrows
  void phase1UsesSeparateReadOnlyDataSourceAndPhase2CatchesUpOnPrimary() {
    ConnectionCountingAndBlockingDataSource p1CatchupDataSource =
        beanFactory.getBean(
            P1_CATCHUP_DATASOURCE_BEAN_NAME, ConnectionCountingAndBlockingDataSource.class);
    DataSourceRecordingPgCatchupFactory catchupFactory =
        (DataSourceRecordingPgCatchupFactory) beanFactory.getBean(PgCatchupFactory.class);

    assertThat(p1CatchupDataSource.opensReadOnlyConnections()).isTrue();

    store.publish(
        List.of(
            Fact.builder().ns(NS).type("test").buildWithoutPayload(),
            Fact.builder().ns(NS).type("test").buildWithoutPayload()));

    p1CatchupDataSource.reset();
    catchupFactory.reset();
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

    List<CatchupPhaseDataSource> catchupDataSources = catchupFactory.catchupDataSources();
    assertThat(catchupDataSources).hasSize(2);
    assertThat(catchupDataSources.get(0).phase()).isEqualTo(PgCatchupFactory.Phase.PHASE_1);
    assertThat(catchupDataSources.get(1).phase()).isEqualTo(PgCatchupFactory.Phase.PHASE_2);
    assertThat(catchupDataSources.get(0).dataSource())
        .isNotSameAs(catchupDataSources.get(1).dataSource());
  }

  @Configuration
  static class P1OffloadTestConfig {

    @Bean
    static BeanPostProcessor wrapBeansForObservability() {
      return new BeanPostProcessor() {
        @Override
        public Object postProcessAfterInitialization(
            @NonNull Object bean, @NonNull String beanName) {
          if (P1_CATCHUP_DATASOURCE_BEAN_NAME.equals(beanName)
              && bean instanceof DataSource dataSource
              && !(bean instanceof ConnectionCountingAndBlockingDataSource)) {
            return new ConnectionCountingAndBlockingDataSource(dataSource);
          }
          if (PG_CATCHUP_FACTORY_BEAN_NAME.equals(beanName)
              && bean instanceof PgCatchupFactory catchupFactory
              && !(bean instanceof DataSourceRecordingPgCatchupFactory)) {
            return new DataSourceRecordingPgCatchupFactory(catchupFactory);
          }
          return bean;
        }
      };
    }
  }

  record CatchupPhaseDataSource(
      PgCatchupFactory.Phase phase, SingleConnectionDataSource dataSource) {}

  static class DataSourceRecordingPgCatchupFactory implements PgCatchupFactory {

    private final PgCatchupFactory delegate;

    @Getter
    private final List<CatchupPhaseDataSource> catchupDataSources = new CopyOnWriteArrayList<>();

    DataSourceRecordingPgCatchupFactory(PgCatchupFactory delegate) {
      this.delegate = delegate;
    }

    @Override
    public PgCatchup create(
        @NonNull SubscriptionRequestTO request,
        @NonNull ServerPipeline pipeline,
        @NonNull AtomicLong serial,
        @NonNull CurrentStatementHolder holder,
        @NonNull SingleConnectionDataSource ds,
        @NonNull Phase phase) {
      catchupDataSources.add(new CatchupPhaseDataSource(phase, ds));
      return delegate.create(request, pipeline, serial, holder, ds, phase);
    }

    void reset() {
      catchupDataSources.clear();
    }
  }

  static class ConnectionCountingAndBlockingDataSource extends DelegatingDataSource {

    @Getter private final DataSource target;
    @Getter private final AtomicInteger connections = new AtomicInteger();
    private final AtomicReference<Throwable> queryBlockingFailure = new AtomicReference<>();
    private final CountDownLatch phase1QueryStarted = new CountDownLatch(1);
    private final CountDownLatch releasePhase1Query = new CountDownLatch(1);
    private final AtomicBoolean holdNextCatchupQuery = new AtomicBoolean();

    ConnectionCountingAndBlockingDataSource(DataSource dataSource) {
      super(dataSource);
      target = dataSource;
    }

    @Override
    public @NonNull Connection getConnection() throws SQLException {
      connections.incrementAndGet();
      return wrapConnection(super.getConnection());
    }

    @Override
    public @NonNull Connection getConnection(@NonNull String username, @NonNull String password)
        throws SQLException {
      connections.incrementAndGet();
      return wrapConnection(super.getConnection(username, password));
    }

    private Connection wrapConnection(Connection connection) throws SQLException {
      Connection countingConnection = mock(Connection.class, delegatesTo(connection));
      doAnswer(
              invocation -> {
                String sql = invocation.getArgument(0);
                PreparedStatement statement = connection.prepareStatement(sql);
                return isCatchupQuery(sql) ? countAndBlockCatchupQuery(statement) : statement;
              })
          .when(countingConnection)
          .prepareStatement(anyString());
      return countingConnection;
    }

    @SneakyThrows
    private PreparedStatement countAndBlockCatchupQuery(PreparedStatement statement) {
      PreparedStatement blockedStatement = mock(PreparedStatement.class, delegatesTo(statement));
      doAnswer(
              invocation -> {
                try {
                  Object result = statement.executeQuery();
                  if (holdNextCatchupQuery.compareAndSet(true, false)) {
                    phase1QueryStarted.countDown();
                    releasePhase1Query.await(10, TimeUnit.SECONDS);
                  }
                  return result;
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  queryBlockingFailure.set(e);
                  throw new SQLException("Interrupted while holding phase 1 query", e);
                }
              })
          .when(blockedStatement)
          .executeQuery();
      return blockedStatement;
    }

    private boolean isCatchupQuery(String sql) {
      String normalized = sql.toLowerCase();
      return normalized.contains(" from fact ")
          && normalized.contains(" where ")
          && normalized.contains(" order by ser asc");
    }

    @SneakyThrows
    void awaitPhase1QueryStarted() {
      assertThat(phase1QueryStarted.await(10, TimeUnit.SECONDS)).isTrue();
      assertThat(queryBlockingFailure).hasNullValue();
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
      queryBlockingFailure.set(null);
      holdNextCatchupQuery.set(true);
    }

    @SneakyThrows
    boolean opensReadOnlyConnections() {
      try (Connection connection = getConnection()) {
        return connection.isReadOnly();
      }
    }
  }

  @Getter
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
  }
}
