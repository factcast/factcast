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
package org.factcast.store.benchmark;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import liquibase.integration.spring.SpringLiquibase;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.factcast.core.store.FactStore;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.PgFactStoreConfiguration;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.NOPSchemaRegistry;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.validation.FactValidationAspect;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

final class BenchmarkDatabase implements AutoCloseable {
  private static final int POOL_SIZE = 64;
  private final int batchSize;
  private final int publishers;
  private final String version = requiredProperty("benchmark.version");
  private final boolean batching = Boolean.parseBoolean(requiredProperty("benchmark.batching"));
  private final int publishMaxBatchSize =
      Integer.parseInt(System.getProperty("benchmark.publish-max-batch-size", "500"));
  private final String image = requiredProperty("benchmark.postgres-image");
  private PostgreSQLContainer postgres;
  private DataSource dataSource;
  private JdbcTemplate jdbc;
  private AnnotationConfigApplicationContext context;

  BenchmarkDatabase(int batchSize, int publishers) {
    this.batchSize = batchSize;
    this.publishers = publishers;
  }

  FactStore start() throws Exception {
    try {
      require(
          !LoggerFactory.getLogger("org.factcast.store.internal.PgFactStore").isInfoEnabled(),
          "Store logging must be WARN or ERROR; use the benchmark logback.xml");
      postgres =
          new PostgreSQLContainer(
                  DockerImageName.parse(image).asCompatibleSubstituteFor("postgres"))
              .withDatabaseName("publish_benchmark")
              // Testcontainers defaults to fsync=off. Explicitly benchmark durable disk writes.
              .withCommand(
                  "postgres",
                  "-c",
                  "fsync=on",
                  "-c",
                  "synchronous_commit=on",
                  "-c",
                  "full_page_writes=on");
      postgres.start();
      dataSource = new DataSource();
      String url = postgres.getJdbcUrl();
      dataSource.setUrl(
          url
              + (url.contains("?") ? "&" : "?")
              + "reWriteBatchedInserts=true&connectTimeout=10&socketTimeout=60");
      dataSource.setUsername(postgres.getUsername());
      dataSource.setPassword(postgres.getPassword());
      dataSource.setDriverClassName("org.postgresql.Driver");
      dataSource.setInitialSize(POOL_SIZE);
      dataSource.setMinIdle(POOL_SIZE);
      dataSource.setMaxIdle(POOL_SIZE);
      dataSource.setMaxActive(POOL_SIZE);
      dataSource.setMaxWait(60_000);
      dataSource.setTestOnBorrow(false);
      dataSource.setJmxEnabled(false);
      jdbc = new JdbcTemplate(dataSource);
      jdbc.setQueryTimeout(60);

      // Initialize each release's own schema before starting any listeners or measurement.
      SpringLiquibase liquibase = new SpringLiquibase();
      liquibase.setDataSource(dataSource);
      liquibase.setResourceLoader(new DefaultResourceLoader());
      liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
      liquibase.afterPropertiesSet();

      context = new AnnotationConfigApplicationContext();
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(
              new MapPropertySource(
                  "benchmark",
                  Map.of(
                      "factcast.store.validation-enabled",
                      false,
                      "factcast.store.publish-batch.enabled",
                      batching,
                      "factcast.store.publish-batch.max-batch-size",
                      publishMaxBatchSize)));
      context.registerBean("dataSource", DataSource.class, () -> dataSource);
      context.registerBean("jdbcTemplate", JdbcTemplate.class, () -> jdbc);
      context.registerBean(
          "namedParameterJdbcTemplate",
          NamedParameterJdbcTemplate.class,
          () -> new NamedParameterJdbcTemplate(jdbc));
      context.registerBean(
          "transactionTemplate",
          TransactionTemplate.class,
          () -> new TransactionTemplate(context.getBean(PlatformTransactionManager.class)));
      if (version.startsWith("0.9.")) {
        // 0.9 exposes a public engine factory bean; newer releases configure their transformer.
        context.registerBean(
            "benchmarkJsEngineFactory",
            Class.forName("org.factcast.store.internal.script.graaljs.GraalJSEngineFactory"));
      }
      context.register(PgFactStoreConfiguration.class);
      context.refresh();
      StoreConfigurationProperties properties = context.getBean(StoreConfigurationProperties.class);
      require(!properties.isValidationEnabled(), "Schema validation must be disabled");
      require(!properties.isSchemaRegistryConfigured(), "Schema registry must not be configured");
      require(
          context.getBean(SchemaRegistry.class) instanceof NOPSchemaRegistry,
          "Expected the no-op schema registry");
      require(
          context.getBeanNamesForType(FactValidationAspect.class).length == 0,
          "Validation advice must not be installed");
      if (batching || version.equals("0.12.5") || version.equals("current")) {
        require(
            Boolean.valueOf(batching)
                .equals(new BeanWrapperImpl(properties).getPropertyValue("publishBatch.enabled")),
            "Concurrent publish batching must match the requested setting");
      }
      if (batching) {
        require(
            Integer.valueOf(publishMaxBatchSize)
                .equals(
                    new BeanWrapperImpl(properties).getPropertyValue("publishBatch.maxBatchSize")),
            "Concurrent publish batch limit must match the requested value");
      }
      FactStore store = context.getBean(FactStore.class);
      require(AopUtils.isAopProxy(store), "The store must retain Spring transaction advice");
      require(
          jdbc.queryForObject("SELECT count(*) FROM fact", Long.class) == 0,
          "Each case must start with an empty fact table");
      return store;
    } catch (Exception | Error failure) {
      close();
      throw failure;
    }
  }

  void verifyAndReport(long acknowledged) throws Exception {
    long persisted = jdbc.queryForObject("SELECT count(*) FROM fact", Long.class);
    require(
        acknowledged > 0 && persisted == acknowledged,
        "Persisted event count differs from successful publication acknowledgements");
    Map<String, Object> database = new LinkedHashMap<>();
    for (String setting :
        new String[] {"server_version", "fsync", "synchronous_commit", "full_page_writes"}) {
      database.put(setting, jdbc.queryForObject("SHOW " + setting, String.class));
    }
    for (String setting : new String[] {"fsync", "synchronous_commit", "full_page_writes"}) {
      require(
          "on".equals(database.get(setting)), "PostgreSQL durability must be enabled: " + setting);
    }
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("version", version);
    metadata.put("commit", requiredProperty("benchmark.commit"));
    metadata.put("batchSize", batchSize);
    metadata.put("publishers", publishers);
    metadata.put("batchingEnabled", batching);
    metadata.put("publishMaxBatchSize", batching ? publishMaxBatchSize : null);
    metadata.put("validationEnabled", false);
    metadata.put("schemaRegistryConfigured", false);
    metadata.put("poolSize", POOL_SIZE);
    metadata.put("reWriteBatchedInserts", true);
    metadata.put("payloadBytes", 256);
    metadata.put(
        "verboseStoreLogging",
        LoggerFactory.getLogger("org.factcast.store.internal.PgFactStore").isInfoEnabled());
    metadata.put("postgresImage", image);
    metadata.put("database", database);
    metadata.put("acknowledgedEvents", acknowledged);
    metadata.put("persistedEvents", persisted);
    metadata.put("javaVersion", System.getProperty("java.version"));
    metadata.put("jvmArguments", ManagementFactory.getRuntimeMXBean().getInputArguments());
    Files.writeString(
        Path.of(requiredProperty("benchmark.metadata")), FactCastJson.writeValueAsString(metadata));
  }

  @Override
  public void close() {
    try {
      if (context != null) {
        context.close();
      }
    } finally {
      try {
        if (dataSource != null) {
          dataSource.close();
        }
      } finally {
        if (postgres != null) {
          postgres.stop();
        }
      }
    }
  }

  private static String requiredProperty(String name) {
    String value = System.getProperty(name);
    require(
        value != null && !value.isBlank(),
        "Run through benchmarks/concurrent-publish/run.py; missing " + name);
    return value;
  }

  private static void require(boolean condition, String message) {
    if (!condition) {
      throw new IllegalStateException(message);
    }
  }
}
