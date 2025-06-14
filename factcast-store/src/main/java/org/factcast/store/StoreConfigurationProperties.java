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
package org.factcast.store;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import java.util.*;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = StoreConfigurationProperties.PROPERTIES_PREFIX)
@Data
@Slf4j
@Accessors(fluent = false)
@Validated
public class StoreConfigurationProperties implements InitializingBean {

  public static final String PROPERTIES_PREFIX = "factcast.store";

  /** defines the fetchSize of a database query */
  @Positive int pageSize = 50;

  /** defines the max number of Facts being scheduled for transformation */
  @Positive
  @Max(32000)
  int transformationCachePageSize = 100;

  /**
   * Optional URL to a Schema Registry. If this is null, validation will be disabled and a warning
   * will be issued. (Defaults to null) Currently a String type due to the fact that "classpath:" is
   * a spring-only protocol
   */
  String schemaRegistryUrl;

  /**
   * If a schemaRegistryUrl is set, you can still decide to enable or disable validation based on
   * the definitions there. This is mostly useful for batch insertions during a bigger migration for
   * example where your facts are already validated, and you want to reduce load on the FactCast
   * server.
   */
  boolean validationEnabled = true;

  /**
   * If validation is enabled, this controls if the local snapshot of the registry is persisted to
   * psql or just kept in mem. (Defaults to true)
   */
  boolean persistentRegistry = true;

  /**
   * when using the persistent impl of the transformation cache, this is the min number of days a
   * transformation result is not read in order to be considered stale. This should free some space
   * in a regular cleanup job
   */
  @Positive int deleteTransformationsStaleForDays = 14;

  /**
   * If validation is enabled, this controls if transformed facts are persistently cached in
   * postgres, rather than in memory. (Defaults to false)
   */
  boolean persistentTransformationCache;

  /**
   * when using the inmem impl of the transformation cache, this is the max number of entries
   * cached.
   */
  @Positive
  @Min(100)
  int inMemTransformationCacheCapacity = 100;

  /**
   * If validation is enabled, this controls if publishing facts, that are not validatable (due to
   * missing meta-data or due to missing schema in the registry) are allowed to be published or
   * should be rejected. (Defaults to false)
   */
  boolean allowUnvalidatedPublish;

  /**
   * If a schema can be replaced by an updated version from the registry (not a good idea in
   * production environments)
   */
  boolean allowSchemaReplace;

  /**
   * Controls how long to block waiting for new notifications from the database (Postgres LISTEN/
   * NOTIFY mechanism). When this time exceeds the health of the database connection is checked.
   * After that waiting for new notifications is repeated.
   */
  @Min(5000)
  int factNotificationBlockingWaitTimeInMillis = 1000 * 15;

  /**
   * When Factcast did not receive any notifications after factNotificationBlockingWaitTimeInMillis
   * milliseconds it validates the health of the database connection. For this purpose it sends an
   * internal notification to the database and waits for the given time to receive back an answer.
   *
   * <p>If the time is exceeded the database connection is renewed.
   */
  @Min(50)
  int factNotificationMaxRoundTripLatencyInMillis = 200;

  /**
   * How much time to wait between invalidating and acquiring a new connection. Note: This parameter
   * is only applied in the part of Factcast which deals with receiving and forwarding database
   * notifications.
   */
  @Min(10)
  int factNotificationNewConnectionWaitTimeInMillis = 100;

  /**
   * If this is set to true, all process-internal caches are bypassed (unless they are essential,
   * like schemareg). That makes it possible to wipe the database between integration tests in order
   * to prevent side-effects.
   */
  boolean integrationTestMode;

  /** tail indexing feature state */
  boolean tailIndexingEnabled = true;

  /** defines, if tail indexes should enable the fastUpdate feature */
  boolean tailIndexingFastUpdateEnabled = false;

  /** parameter will only be used, if fastUpdate is enabled */
  int tailIndexingPendingListLimit = 4096;

  /**
   * the number of tail indexes to keep. The higher the number, the slower the inserts. Probably 2
   * or 3 is a good value unless you have a very high tail rebuild frequency and not permanently
   * connected applications (like offline clients for instance)
   */
  @Positive
  @Max(128)
  int tailGenerationsToKeep = 3;

  /** Minimum age of the youngest tail index, before a new one is created. Defaults to 7 days */
  Duration minimumTailAge = Duration.ofDays(7);

  /** do not change the default here, see PGTailIndexManagerImpl::triggerTailCreation */
  String tailManagementCron = "0 0 0 * * *";

  /**
   * Index creation can hang for a long time in case of many open transactions.
   *
   * <p>Abort after given timeout.
   *
   * <p>In case you want to give it a new try with every run, specify a time slighly shorter than
   * the time between two runs, e.g. 23h59m if the cron job runs 24 hours.
   */
  Duration tailCreationTimeout = Duration.ofDays(1).minusMinutes(1);

  /**
   * This is the number of threads we create for handling new subscriptions requests. It's
   * implemented via a fixed thread pool. As soon as the subscription request finishes or enters
   * phase 3 (follow) the thread is freed up again. In earlier versions we used the common FJP which
   * limits the parallelism to the number of cores - 1. If you ever encounter too much database load
   * or too high waiting time for subscriptions this can be an option.
   */
  int sizeOfThreadPoolForSubscriptions = 100;

  /**
   * This is the number of threads we create for handling buffered transformations. It's implemented
   * via work stealing thread pool. In early versions we used the common FJP which limits the
   * parallelism to the number of cores - 1.
   */
  int sizeOfThreadPoolForBufferedTransformations = 25;

  /**
   * Configures the FactStore to work in read-only mode. You cannot publish any events in this mode
   * and certain functionality like tail index generation or state token generation is disabled.
   *
   * <p>You can still use a persistent schema store and transformation cache, however they will work
   * in read-only mode. Additionally, liquibase is disabled.
   */
  boolean readOnlyModeEnabled;

  /**
   * used to direct the enumerateTypes/Namespaces calls against the store directly, thus bypass the
   * schema-registry even it is configured. This is useful, if you want to see ns/types that are not
   * yet found in the registry, but exist in the factStore.
   */
  boolean enumerationDirectModeEnabled;

  public boolean isSchemaRegistryConfigured() {
    return schemaRegistryUrl != null;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if (integrationTestMode) {

      adjustLogbackAppender();

      log.warn(
          "**** You are running in INTEGRATION TEST MODE. If you see this in production, "
              + "this would be a good time to panic. (See "
              + PROPERTIES_PREFIX
              + ".integrationTestMode) ****");
    }

    if (!isSchemaRegistryConfigured()) {
      log.warn(
          "**** SchemaRegistry-mode is disabled. Fact validation will not happen. This is"
              + " discouraged for production environments. You have been warned. ****");

    } else {
      if (!isValidationEnabled()) {
        log.warn(
            "**** SchemaRegistry-mode is enabled but validation of Facts is disabled. This is"
                + " discouraged for production environments. You have been warned. ****");
      }
    }
  }

  private void adjustLogbackAppender() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    for (Logger logger : context.getLoggerList()) {
      Iterator<Appender<ILoggingEvent>> iter = logger.iteratorForAppenders();
      while (iter.hasNext()) {
        Appender<ILoggingEvent> appender = iter.next();
        if (appender instanceof ConsoleAppender) {
          log.debug("Setting {} to immediate flush", appender.getClass());
          ((ConsoleAppender<?>) appender).setImmediateFlush(true);
        }
      }
    }
  }
}
