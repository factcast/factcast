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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import lombok.NonNull;
import nl.altindag.log.LogCaptor;
import org.aopalliance.intercept.MethodInterceptor;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.*;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.internal.catchup.*;
import org.factcast.store.internal.catchup.cursor.PgCursorCatchup;
import org.factcast.store.internal.pipeline.*;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.factcast.test.IntegrationTest;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.postgresql.ds.PGSimpleDataSource;
import org.postgresql.jdbc.PgConnection;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ContextConfiguration(
    classes = {
      PgTestConfiguration.class,
      QueryCancellationIntegrationTest.Config.class,
      QueryCancellationIntegrationTest.DataSourceSpyConfig.class
    })
@Sql(scripts = "/wipe.sql", config = @SqlConfig(separator = "#"))
@ExtendWith(SpringExtension.class)
@IntegrationTest
@TestPropertySource(
    properties = {
      "factcast.store.schemaRegistryUrl=classpath:example-registry",
      "factcast.store.persistentRegistry=false",
      "factcast.store.pageSize=1"
    })
class QueryCancellationIntegrationTest {

  @TestConfiguration
  static class DataSourceSpyConfig {

    /** this tweaks the pool to use a native PgDataSource that returns spied connections */
    @Bean
    static BeanPostProcessor dataSourceWrapper() {
      return new BeanPostProcessor() {

        @Override
        public Object postProcessBeforeInitialization(Object bean, String beanName) {
          if (bean instanceof org.apache.tomcat.jdbc.pool.DataSource tomcatDs) {
            installPhysicalDataSource(tomcatDs);
          }
          return bean;
        }

        private static void installPhysicalDataSource(org.apache.tomcat.jdbc.pool.DataSource pool) {
          PGSimpleDataSource pg = new PGSimpleDataSource();
          pg.setUrl(pool.getUrl());
          pg.setUser(pool.getUsername());
          pg.setPassword(pool.getPassword());
          pool.setDataSource(spyConnections(pg));
        }

        private static DataSource spyConnections(PGSimpleDataSource target) {
          ProxyFactory factory = new ProxyFactory(target);
          factory.setProxyTargetClass(true);

          factory.addAdvice(
              (MethodInterceptor)
                  invocation -> {
                    Object result = invocation.proceed();
                    if (result instanceof PgConnection pgConnection) {
                      return Mockito.spy(pgConnection);
                    }
                    return result;
                  });
          return (DataSource) factory.getProxy();
        }
      };
    }
  }

  static Set<PgConnection> capturedConnections = Collections.synchronizedSet(new HashSet<>());

  @Configuration
  public static class Config {

    /**
     * Here we tweak the catchup strategy to a catchup who's query is really slow while returning
     * rows one by one...
     */
    @Bean
    @Primary
    public PgCatchupFactory catchupFactory(StoreConfigurationProperties props, PgMetrics metrics) {
      return new PgCatchUpFactoryImpl(props, metrics) {
        @Override
        public PgCatchup create(
            @NonNull SubscriptionRequestTO request,
            @NonNull PushbackServerPipeline pipeline,
            @NonNull AtomicLong serial,
            @NonNull SingleConnectionDataSource ds,
            @NonNull Phase phase) {
          return new PgCursorCatchup(props, metrics, request, pipeline, serial, ds, phase) {

            @Override
            protected PgQueryBuilder createPgQueryBuilder(List<FactSpec> specs) {
              return new PgQueryBuilder(specs) {
                public String createSQL() {
                  var sql = super.createSQL();

                  // slow down the query
                  int insertionPoint = sql.indexOf("WHERE");
                  String ret =
                      sql.substring(0, insertionPoint)
                          + " CROSS JOIN LATERAL (SELECT pg_sleep(0.5)) as delay "
                          + sql.substring(insertionPoint);

                  // also we need to omit the sorting for the query to return the first tuple while
                  // still querying, otherwise we'd wait for the query to finish.
                  ret = ret.replace("ORDER BY ser ASC", "");

                  return ret;
                }
              };
            }

            /**
             * this one captures the connection used by the statement and adds a flush after every
             * signal to the pipeline so that we do not have to wait for the buffer to be flushed.
             */
            @Override
            protected RowCallbackHandler createRowCallbackHandler(PgFactExtractor extractor) {
              RowCallbackHandler rowCallbackHandler = super.createRowCallbackHandler(extractor);
              return rs -> {
                try {
                  capturedConnections.add(
                      rs.getStatement().getConnection().unwrap(PgConnection.class));
                } catch (SQLException meh) {
                }
                rowCallbackHandler.processRow(rs);
                // we sneak in a flush after every signal
                pipeline.process(Signal.flush());
              };
            }
          };
        }
      };
    }
  }

  @Autowired FactStore store;

  @Test
  void testTransformationExceptionCancelsServerPipeline() throws Exception {
    // Publish a fact of version 1
    for (int i = 0; i < 15; i++) {
      store.publish(
          List.of(
              Fact.builder()
                  .ns("ns")
                  .type("type")
                  .version(1)
                  .build("{\"firstName\":\"Peter\",\"lastName\":\"Peterson\"}")));
    }

    Collection<FactSpec> spec =
        Collections.singletonList(FactSpec.ns("ns").type("type").version(42));
    SubscriptionRequest request = SubscriptionRequest.catchup(spec).fromScratch();

    AtomicReference<Throwable> receivedError = new AtomicReference<>();

    FactObserver observer =
        new FactObserver() {
          @Override
          public void onNext(@NonNull Fact element) {}

          @Override
          public void onError(@NonNull Throwable throwable) {
            receivedError.set(throwable);
          }

          @Override
          public void onComplete() {}
        };

    try (LogCaptor logCaptor = LogCaptor.forClass(CatchupDataSource.class)) {
      logCaptor.setLogLevelToDebug();

      var subscription = store.subscribe(SubscriptionRequestTO.from(request), observer);

      try {
        subscription.awaitComplete(5000);
      } catch (Exception expected) {
        // expected error or closed exception
      }

      // Verify that TransformationException was received by subscriber
      assertThat(receivedError.get()).isInstanceOf(TransformationException.class);
      assertThat(receivedError.get().getMessage())
          .startsWith("Cannot reach any version in [42] from version 1");

      // Verify that "Cancellation requested" is logged
      assertThat(logCaptor.getDebugLogs()).contains("Cancellation requested");

      // Verify that cancelQuery() was called on the database connection
      assertThat(capturedConnections).hasSize(1);
      verify(capturedConnections.iterator().next(), times(1)).cancelQuery();
    }
  }
}
