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

import java.time.Duration;
import java.util.*;
import java.util.stream.*;

import javax.annotation.Nonnull;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.*;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@SuppressWarnings({"DefaultAnnotationParam", "OptionalUsedAsFieldOrParameterType"})
@ConfigurationProperties(prefix = PgLegacyConfigurationProperties.LEGACY_PREFIX)
@EqualsAndHashCode
@ToString
@Deprecated
public class PgLegacyConfigurationProperties implements ApplicationListener<ApplicationReadyEvent> {

  public static final String LEGACY_PREFIX = "factcast.store.pgsql";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(PgLegacyConfigurationProperties.class);

  @Autowired private Environment env;

  /**
   * defines the number of Facts being retrieved with one Page Query for PageStrategy.PAGED, or
   * respectively the fetchSize when using PageStrategy.FETCHING
   */
  private Optional<Integer> pageSize = Optional.empty();
  /** Defines the Strategy used for Paging in the Catchup Phase. */
  private Optional<CatchupStrategy> catchupStrategy = Optional.empty();

  /**
   * Optional URL to a Schema Registry. If this is null, validation will be disabled and a warning
   * will be issued. (Defaults to null) Currently a String type due to the fact that "classpath:" is
   * a spring-only protocol
   */
  private Optional<String> schemaRegistryUrl = Optional.empty();

  /**
   * If validation is enabled, this controls if the local snapshot of the registry is persisted to
   * psql or just kept in mem. (Defaults to true)
   */
  private Optional<Boolean> persistentRegistry = Optional.empty();

  /**
   * when using the persistent impl of the transformation cache, this is the min number of days a
   * transformation result is not read in order to be considered stale. This should free some space
   * in a regular cleanup job
   */
  private Optional<Integer> deleteTransformationsStaleForDays = Optional.empty();

  /**
   * this is the min number of days a snapshot is not read in order to be considered stale. This
   * should free some space in a regular cleanup job
   */
  private Optional<Integer> deleteSnapshotStaleForDays = Optional.empty();

  /**
   * this controls if transformed facts are persistently cached in postgres, rather than in memory.
   * (Defaults to false)
   */
  private Optional<Boolean> persistentTransformationCache = Optional.empty();

  /**
   * when using the inmem impl of the transformation cache, this is the max number of entries
   * cached.
   */
  private Optional<Integer> inMemTransformationCacheCapacity = Optional.empty();

  /**
   * If validation is enabled, this controls if publishing facts, that are not validatable (due to
   * missing meta-data or due to missing schema in the registry) are allowed to be published or
   * should be rejected. (Defaults to false)
   */
  private Optional<Boolean> allowUnvalidatedPublish = Optional.empty();

  /**
   * If a schema can be replaced by an updated version from the registry (not a good idea in
   * production environments)
   */
  private Optional<Boolean> allowSchemaReplace = Optional.empty();

  /**
   * Controls how long to block waiting for new notifications from the database (Postgres LISTEN/
   * NOTIFY mechanism). When this time exceeds the health of the database connection is checked.
   * After that waiting for new notifications is repeated.
   */
  private Optional<Integer> factNotificationBlockingWaitTimeInMillis = Optional.empty();

  /**
   * When Factcast did not receive any notifications after factNotificationBlockingWaitTimeInMillis
   * milliseconds it validates the health of the database connection. For this purpose it sends an
   * internal notification to the database and waits for the given time to receive back an answer.
   *
   * <p>If the time is exceeded the database connection is renewed.
   */
  private Optional<Integer> factNotificationMaxRoundTripLatencyInMillis = Optional.empty();

  /**
   * How much time to wait between invalidating and acquiring a new connection. Note: This parameter
   * is only applied in the part of Factcast which deals with receiving and forwarding database
   * notifications.
   */
  private Optional<Integer> factNotificationNewConnectionWaitTimeInMillis = Optional.empty();

  /**
   * If this is set to true, all process-internal caches are bypassed (unless they are essential,
   * like schemareg). That makes it possible to wipe the database between integration tests in order
   * to prevent side-effects.
   */
  private Optional<Boolean> integrationTestMode = Optional.empty();

  /** tail indexing feature state */
  private Optional<Boolean> tailIndexingEnabled = Optional.empty();

  /**
   * the number of tail indexes to keep. The higher the number, the slower the inserts. Probably 2
   * or 3 is a good value unless you have a very high tail rebuild frequency and not permanently
   * connected applications (like offline clients for instance)
   */
  private Optional<Integer> tailGenerationsToKeep = Optional.empty();

  /**
   * Minimum tail age. Tail rotation will be skipped, unless the age of the youngest existing tail
   * is at least this old. Defaults to 7 days
   */
  private Optional<Duration> minimumTailAge = Optional.empty();

  /** do not change the default here, see PGTailIndexManagerImpl::triggerTailCreation */
  private Optional<String> tailManagementCron = Optional.empty();

  public PgLegacyConfigurationProperties() {}

  @Override
  public void onApplicationEvent(@Nonnull ApplicationReadyEvent event) {
    List<Map.Entry<String, Object>> legacyProperties =
        findAllProperties().entrySet().stream()
            .filter(e -> e.getKey().startsWith(LEGACY_PREFIX))
            .collect(Collectors.toList());

    if (!legacyProperties.isEmpty()) {
      log.warn(
          "There are legacy properties detected. Property namespace has been renamed from '"
              + LEGACY_PREFIX
              + "' to 'factcast.store'");
      legacyProperties.forEach(p -> log.warn("Property {} found in {}", p.getKey(), p.getValue()));
    }
  }

  private Map<String, Object> findAllProperties() {
    Map<String, Object> map = new HashMap<>();
    MutablePropertySources propertySources = ((AbstractEnvironment) env).getPropertySources();
    for (PropertySource<?> propertySource : propertySources) {
      if (propertySource instanceof MapPropertySource) {
        Map<String, Object> source = ((MapPropertySource) propertySource).getSource();
        source.forEach((key, value) -> map.put(key, propertySource.toString()));
      }
    }
    return map;
  }

  public PgLegacyConfigurationProperties setEnv(Environment env) {
    this.env = env;
    return this;
  }

  public PgLegacyConfigurationProperties setPageSize(Optional<Integer> pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public PgLegacyConfigurationProperties setCatchupStrategy(
      Optional<CatchupStrategy> catchupStrategy) {
    this.catchupStrategy = catchupStrategy;
    return this;
  }

  public PgLegacyConfigurationProperties setSchemaRegistryUrl(Optional<String> schemaRegistryUrl) {
    this.schemaRegistryUrl = schemaRegistryUrl;
    return this;
  }

  public PgLegacyConfigurationProperties setPersistentRegistry(
      Optional<Boolean> persistentRegistry) {
    this.persistentRegistry = persistentRegistry;
    return this;
  }

  public PgLegacyConfigurationProperties setDeleteTransformationsStaleForDays(
      Optional<Integer> deleteTransformationsStaleForDays) {
    this.deleteTransformationsStaleForDays = deleteTransformationsStaleForDays;
    return this;
  }

  public PgLegacyConfigurationProperties setDeleteSnapshotStaleForDays(
      Optional<Integer> deleteSnapshotStaleForDays) {
    this.deleteSnapshotStaleForDays = deleteSnapshotStaleForDays;
    return this;
  }

  public PgLegacyConfigurationProperties setPersistentTransformationCache(
      Optional<Boolean> persistentTransformationCache) {
    this.persistentTransformationCache = persistentTransformationCache;
    return this;
  }

  public PgLegacyConfigurationProperties setInMemTransformationCacheCapacity(
      Optional<Integer> inMemTransformationCacheCapacity) {
    this.inMemTransformationCacheCapacity = inMemTransformationCacheCapacity;
    return this;
  }

  public PgLegacyConfigurationProperties setAllowUnvalidatedPublish(
      Optional<Boolean> allowUnvalidatedPublish) {
    this.allowUnvalidatedPublish = allowUnvalidatedPublish;
    return this;
  }

  public PgLegacyConfigurationProperties setAllowSchemaReplace(
      Optional<Boolean> allowSchemaReplace) {
    this.allowSchemaReplace = allowSchemaReplace;
    return this;
  }

  public PgLegacyConfigurationProperties setFactNotificationBlockingWaitTimeInMillis(
      Optional<Integer> factNotificationBlockingWaitTimeInMillis) {
    this.factNotificationBlockingWaitTimeInMillis = factNotificationBlockingWaitTimeInMillis;
    return this;
  }

  public PgLegacyConfigurationProperties setFactNotificationMaxRoundTripLatencyInMillis(
      Optional<Integer> factNotificationMaxRoundTripLatencyInMillis) {
    this.factNotificationMaxRoundTripLatencyInMillis = factNotificationMaxRoundTripLatencyInMillis;
    return this;
  }

  public PgLegacyConfigurationProperties setFactNotificationNewConnectionWaitTimeInMillis(
      Optional<Integer> factNotificationNewConnectionWaitTimeInMillis) {
    this.factNotificationNewConnectionWaitTimeInMillis =
        factNotificationNewConnectionWaitTimeInMillis;
    return this;
  }

  public PgLegacyConfigurationProperties setIntegrationTestMode(
      Optional<Boolean> integrationTestMode) {
    this.integrationTestMode = integrationTestMode;
    return this;
  }

  public PgLegacyConfigurationProperties setTailIndexingEnabled(
      Optional<Boolean> tailIndexingEnabled) {
    this.tailIndexingEnabled = tailIndexingEnabled;
    return this;
  }

  public PgLegacyConfigurationProperties setTailGenerationsToKeep(
      Optional<Integer> tailGenerationsToKeep) {
    this.tailGenerationsToKeep = tailGenerationsToKeep;
    return this;
  }

  public PgLegacyConfigurationProperties setMinimumTailAge(Optional<Duration> minimumTailAge) {
    this.minimumTailAge = minimumTailAge;
    return this;
  }

  public Environment getEnv() {
    return this.env;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getPageSize() {
    return this.pageSize;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<CatchupStrategy> getCatchupStrategy() {
    return this.catchupStrategy;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<String> getSchemaRegistryUrl() {
    return this.schemaRegistryUrl;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Boolean> getPersistentRegistry() {
    return this.persistentRegistry;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getDeleteTransformationsStaleForDays() {
    return this.deleteTransformationsStaleForDays;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getDeleteSnapshotStaleForDays() {
    return this.deleteSnapshotStaleForDays;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Boolean> getPersistentTransformationCache() {
    return this.persistentTransformationCache;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getInMemTransformationCacheCapacity() {
    return this.inMemTransformationCacheCapacity;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Boolean> getAllowUnvalidatedPublish() {
    return this.allowUnvalidatedPublish;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Boolean> getAllowSchemaReplace() {
    return this.allowSchemaReplace;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getFactNotificationBlockingWaitTimeInMillis() {
    return this.factNotificationBlockingWaitTimeInMillis;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getFactNotificationMaxRoundTripLatencyInMillis() {
    return this.factNotificationMaxRoundTripLatencyInMillis;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getFactNotificationNewConnectionWaitTimeInMillis() {
    return this.factNotificationNewConnectionWaitTimeInMillis;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Boolean> getIntegrationTestMode() {
    return this.integrationTestMode;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Boolean> getTailIndexingEnabled() {
    return this.tailIndexingEnabled;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Integer> getTailGenerationsToKeep() {
    return this.tailGenerationsToKeep;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<Duration> getMinimumTailAge() {
    return this.minimumTailAge;
  }

  @DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")
  public Optional<String> getTailManagementCron() {
    return this.tailManagementCron;
  }
}
