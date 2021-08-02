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

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.*;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@SuppressWarnings({"DefaultAnnotationParam", "OptionalUsedAsFieldOrParameterType"})
@ConfigurationProperties(prefix = PgConfigurationLegacyProperties.LEGACY_PREFIX)
public class PgConfigurationLegacyProperties implements ApplicationListener<ApplicationReadyEvent> {

  public static final String LEGACY_PREFIX = "factcast.store.pgsql";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(PgConfigurationLegacyProperties.class);

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
   * If validation is enabled, this controls if transformed facts are persistently cached in
   * postgres, rather than in memory. (Defaults to false)
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

  public PgConfigurationLegacyProperties() {}

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
      legacyProperties.forEach(p -> log.error("Property {} found in {}", p.getKey(), p.getValue()));
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

  public PgConfigurationLegacyProperties setEnv(Environment env) {
    this.env = env;
    return this;
  }

  public PgConfigurationLegacyProperties setPageSize(Optional<Integer> pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  public PgConfigurationLegacyProperties setCatchupStrategy(
      Optional<CatchupStrategy> catchupStrategy) {
    this.catchupStrategy = catchupStrategy;
    return this;
  }

  public PgConfigurationLegacyProperties setSchemaRegistryUrl(Optional<String> schemaRegistryUrl) {
    this.schemaRegistryUrl = schemaRegistryUrl;
    return this;
  }

  public PgConfigurationLegacyProperties setPersistentRegistry(
      Optional<Boolean> persistentRegistry) {
    this.persistentRegistry = persistentRegistry;
    return this;
  }

  public PgConfigurationLegacyProperties setDeleteTransformationsStaleForDays(
      Optional<Integer> deleteTransformationsStaleForDays) {
    this.deleteTransformationsStaleForDays = deleteTransformationsStaleForDays;
    return this;
  }

  public PgConfigurationLegacyProperties setDeleteSnapshotStaleForDays(
      Optional<Integer> deleteSnapshotStaleForDays) {
    this.deleteSnapshotStaleForDays = deleteSnapshotStaleForDays;
    return this;
  }

  public PgConfigurationLegacyProperties setPersistentTransformationCache(
      Optional<Boolean> persistentTransformationCache) {
    this.persistentTransformationCache = persistentTransformationCache;
    return this;
  }

  public PgConfigurationLegacyProperties setInMemTransformationCacheCapacity(
      Optional<Integer> inMemTransformationCacheCapacity) {
    this.inMemTransformationCacheCapacity = inMemTransformationCacheCapacity;
    return this;
  }

  public PgConfigurationLegacyProperties setAllowUnvalidatedPublish(
      Optional<Boolean> allowUnvalidatedPublish) {
    this.allowUnvalidatedPublish = allowUnvalidatedPublish;
    return this;
  }

  public PgConfigurationLegacyProperties setAllowSchemaReplace(
      Optional<Boolean> allowSchemaReplace) {
    this.allowSchemaReplace = allowSchemaReplace;
    return this;
  }

  public PgConfigurationLegacyProperties setFactNotificationBlockingWaitTimeInMillis(
      Optional<Integer> factNotificationBlockingWaitTimeInMillis) {
    this.factNotificationBlockingWaitTimeInMillis = factNotificationBlockingWaitTimeInMillis;
    return this;
  }

  public PgConfigurationLegacyProperties setFactNotificationMaxRoundTripLatencyInMillis(
      Optional<Integer> factNotificationMaxRoundTripLatencyInMillis) {
    this.factNotificationMaxRoundTripLatencyInMillis = factNotificationMaxRoundTripLatencyInMillis;
    return this;
  }

  public PgConfigurationLegacyProperties setFactNotificationNewConnectionWaitTimeInMillis(
      Optional<Integer> factNotificationNewConnectionWaitTimeInMillis) {
    this.factNotificationNewConnectionWaitTimeInMillis =
        factNotificationNewConnectionWaitTimeInMillis;
    return this;
  }

  public PgConfigurationLegacyProperties setIntegrationTestMode(
      Optional<Boolean> integrationTestMode) {
    this.integrationTestMode = integrationTestMode;
    return this;
  }

  public PgConfigurationLegacyProperties setTailIndexingEnabled(
      Optional<Boolean> tailIndexingEnabled) {
    this.tailIndexingEnabled = tailIndexingEnabled;
    return this;
  }

  public PgConfigurationLegacyProperties setTailGenerationsToKeep(
      Optional<Integer> tailGenerationsToKeep) {
    this.tailGenerationsToKeep = tailGenerationsToKeep;
    return this;
  }

  public PgConfigurationLegacyProperties setMinimumTailAge(Optional<Duration> minimumTailAge) {
    this.minimumTailAge = minimumTailAge;
    return this;
  }

  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof PgConfigurationLegacyProperties)) return false;
    final PgConfigurationLegacyProperties other = (PgConfigurationLegacyProperties) o;
    if (!other.canEqual((Object) this)) return false;
    final Object this$env = this.getEnv();
    final Object other$env = other.getEnv();
    if (this$env == null ? other$env != null : !this$env.equals(other$env)) return false;
    final Object this$pageSize = this.getPageSize();
    final Object other$pageSize = other.getPageSize();
    if (this$pageSize == null ? other$pageSize != null : !this$pageSize.equals(other$pageSize))
      return false;
    final Object this$catchupStrategy = this.getCatchupStrategy();
    final Object other$catchupStrategy = other.getCatchupStrategy();
    if (this$catchupStrategy == null
        ? other$catchupStrategy != null
        : !this$catchupStrategy.equals(other$catchupStrategy)) return false;
    final Object this$schemaRegistryUrl = this.getSchemaRegistryUrl();
    final Object other$schemaRegistryUrl = other.getSchemaRegistryUrl();
    if (this$schemaRegistryUrl == null
        ? other$schemaRegistryUrl != null
        : !this$schemaRegistryUrl.equals(other$schemaRegistryUrl)) return false;
    final Object this$persistentRegistry = this.getPersistentRegistry();
    final Object other$persistentRegistry = other.getPersistentRegistry();
    if (this$persistentRegistry == null
        ? other$persistentRegistry != null
        : !this$persistentRegistry.equals(other$persistentRegistry)) return false;
    final Object this$deleteTransformationsStaleForDays =
        this.getDeleteTransformationsStaleForDays();
    final Object other$deleteTransformationsStaleForDays =
        other.getDeleteTransformationsStaleForDays();
    if (this$deleteTransformationsStaleForDays == null
        ? other$deleteTransformationsStaleForDays != null
        : !this$deleteTransformationsStaleForDays.equals(other$deleteTransformationsStaleForDays))
      return false;
    final Object this$deleteSnapshotStaleForDays = this.getDeleteSnapshotStaleForDays();
    final Object other$deleteSnapshotStaleForDays = other.getDeleteSnapshotStaleForDays();
    if (this$deleteSnapshotStaleForDays == null
        ? other$deleteSnapshotStaleForDays != null
        : !this$deleteSnapshotStaleForDays.equals(other$deleteSnapshotStaleForDays)) return false;
    final Object this$persistentTransformationCache = this.getPersistentTransformationCache();
    final Object other$persistentTransformationCache = other.getPersistentTransformationCache();
    if (this$persistentTransformationCache == null
        ? other$persistentTransformationCache != null
        : !this$persistentTransformationCache.equals(other$persistentTransformationCache))
      return false;
    final Object this$inMemTransformationCacheCapacity = this.getInMemTransformationCacheCapacity();
    final Object other$inMemTransformationCacheCapacity =
        other.getInMemTransformationCacheCapacity();
    if (this$inMemTransformationCacheCapacity == null
        ? other$inMemTransformationCacheCapacity != null
        : !this$inMemTransformationCacheCapacity.equals(other$inMemTransformationCacheCapacity))
      return false;
    final Object this$allowUnvalidatedPublish = this.getAllowUnvalidatedPublish();
    final Object other$allowUnvalidatedPublish = other.getAllowUnvalidatedPublish();
    if (this$allowUnvalidatedPublish == null
        ? other$allowUnvalidatedPublish != null
        : !this$allowUnvalidatedPublish.equals(other$allowUnvalidatedPublish)) return false;
    final Object this$allowSchemaReplace = this.getAllowSchemaReplace();
    final Object other$allowSchemaReplace = other.getAllowSchemaReplace();
    if (this$allowSchemaReplace == null
        ? other$allowSchemaReplace != null
        : !this$allowSchemaReplace.equals(other$allowSchemaReplace)) return false;
    final Object this$factNotificationBlockingWaitTimeInMillis =
        this.getFactNotificationBlockingWaitTimeInMillis();
    final Object other$factNotificationBlockingWaitTimeInMillis =
        other.getFactNotificationBlockingWaitTimeInMillis();
    if (this$factNotificationBlockingWaitTimeInMillis == null
        ? other$factNotificationBlockingWaitTimeInMillis != null
        : !this$factNotificationBlockingWaitTimeInMillis.equals(
            other$factNotificationBlockingWaitTimeInMillis)) return false;
    final Object this$factNotificationMaxRoundTripLatencyInMillis =
        this.getFactNotificationMaxRoundTripLatencyInMillis();
    final Object other$factNotificationMaxRoundTripLatencyInMillis =
        other.getFactNotificationMaxRoundTripLatencyInMillis();
    if (this$factNotificationMaxRoundTripLatencyInMillis == null
        ? other$factNotificationMaxRoundTripLatencyInMillis != null
        : !this$factNotificationMaxRoundTripLatencyInMillis.equals(
            other$factNotificationMaxRoundTripLatencyInMillis)) return false;
    final Object this$factNotificationNewConnectionWaitTimeInMillis =
        this.getFactNotificationNewConnectionWaitTimeInMillis();
    final Object other$factNotificationNewConnectionWaitTimeInMillis =
        other.getFactNotificationNewConnectionWaitTimeInMillis();
    if (this$factNotificationNewConnectionWaitTimeInMillis == null
        ? other$factNotificationNewConnectionWaitTimeInMillis != null
        : !this$factNotificationNewConnectionWaitTimeInMillis.equals(
            other$factNotificationNewConnectionWaitTimeInMillis)) return false;
    final Object this$integrationTestMode = this.getIntegrationTestMode();
    final Object other$integrationTestMode = other.getIntegrationTestMode();
    if (this$integrationTestMode == null
        ? other$integrationTestMode != null
        : !this$integrationTestMode.equals(other$integrationTestMode)) return false;
    final Object this$tailIndexingEnabled = this.getTailIndexingEnabled();
    final Object other$tailIndexingEnabled = other.getTailIndexingEnabled();
    if (this$tailIndexingEnabled == null
        ? other$tailIndexingEnabled != null
        : !this$tailIndexingEnabled.equals(other$tailIndexingEnabled)) return false;
    final Object this$tailGenerationsToKeep = this.getTailGenerationsToKeep();
    final Object other$tailGenerationsToKeep = other.getTailGenerationsToKeep();
    if (this$tailGenerationsToKeep == null
        ? other$tailGenerationsToKeep != null
        : !this$tailGenerationsToKeep.equals(other$tailGenerationsToKeep)) return false;
    final Object this$minimumTailAge = this.getMinimumTailAge();
    final Object other$minimumTailAge = other.getMinimumTailAge();
    if (this$minimumTailAge == null
        ? other$minimumTailAge != null
        : !this$minimumTailAge.equals(other$minimumTailAge)) return false;
    return true;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof PgConfigurationLegacyProperties;
  }

  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $env = this.getEnv();
    result = result * PRIME + ($env == null ? 43 : $env.hashCode());
    final Object $pageSize = this.getPageSize();
    result = result * PRIME + ($pageSize == null ? 43 : $pageSize.hashCode());
    final Object $catchupStrategy = this.getCatchupStrategy();
    result = result * PRIME + ($catchupStrategy == null ? 43 : $catchupStrategy.hashCode());
    final Object $schemaRegistryUrl = this.getSchemaRegistryUrl();
    result = result * PRIME + ($schemaRegistryUrl == null ? 43 : $schemaRegistryUrl.hashCode());
    final Object $persistentRegistry = this.getPersistentRegistry();
    result = result * PRIME + ($persistentRegistry == null ? 43 : $persistentRegistry.hashCode());
    final Object $deleteTransformationsStaleForDays = this.getDeleteTransformationsStaleForDays();
    result =
        result * PRIME
            + ($deleteTransformationsStaleForDays == null
                ? 43
                : $deleteTransformationsStaleForDays.hashCode());
    final Object $deleteSnapshotStaleForDays = this.getDeleteSnapshotStaleForDays();
    result =
        result * PRIME
            + ($deleteSnapshotStaleForDays == null ? 43 : $deleteSnapshotStaleForDays.hashCode());
    final Object $persistentTransformationCache = this.getPersistentTransformationCache();
    result =
        result * PRIME
            + ($persistentTransformationCache == null
                ? 43
                : $persistentTransformationCache.hashCode());
    final Object $inMemTransformationCacheCapacity = this.getInMemTransformationCacheCapacity();
    result =
        result * PRIME
            + ($inMemTransformationCacheCapacity == null
                ? 43
                : $inMemTransformationCacheCapacity.hashCode());
    final Object $allowUnvalidatedPublish = this.getAllowUnvalidatedPublish();
    result =
        result * PRIME
            + ($allowUnvalidatedPublish == null ? 43 : $allowUnvalidatedPublish.hashCode());
    final Object $allowSchemaReplace = this.getAllowSchemaReplace();
    result = result * PRIME + ($allowSchemaReplace == null ? 43 : $allowSchemaReplace.hashCode());
    final Object $factNotificationBlockingWaitTimeInMillis =
        this.getFactNotificationBlockingWaitTimeInMillis();
    result =
        result * PRIME
            + ($factNotificationBlockingWaitTimeInMillis == null
                ? 43
                : $factNotificationBlockingWaitTimeInMillis.hashCode());
    final Object $factNotificationMaxRoundTripLatencyInMillis =
        this.getFactNotificationMaxRoundTripLatencyInMillis();
    result =
        result * PRIME
            + ($factNotificationMaxRoundTripLatencyInMillis == null
                ? 43
                : $factNotificationMaxRoundTripLatencyInMillis.hashCode());
    final Object $factNotificationNewConnectionWaitTimeInMillis =
        this.getFactNotificationNewConnectionWaitTimeInMillis();
    result =
        result * PRIME
            + ($factNotificationNewConnectionWaitTimeInMillis == null
                ? 43
                : $factNotificationNewConnectionWaitTimeInMillis.hashCode());
    final Object $integrationTestMode = this.getIntegrationTestMode();
    result = result * PRIME + ($integrationTestMode == null ? 43 : $integrationTestMode.hashCode());
    final Object $tailIndexingEnabled = this.getTailIndexingEnabled();
    result = result * PRIME + ($tailIndexingEnabled == null ? 43 : $tailIndexingEnabled.hashCode());
    final Object $tailGenerationsToKeep = this.getTailGenerationsToKeep();
    result =
        result * PRIME + ($tailGenerationsToKeep == null ? 43 : $tailGenerationsToKeep.hashCode());
    final Object $minimumTailAge = this.getMinimumTailAge();
    result = result * PRIME + ($minimumTailAge == null ? 43 : $minimumTailAge.hashCode());
    return result;
  }

  public String toString() {
    return "PgConfigurationLegacyProperties(env="
        + this.getEnv()
        + ", pageSize="
        + this.getPageSize()
        + ", catchupStrategy="
        + this.getCatchupStrategy()
        + ", schemaRegistryUrl="
        + this.getSchemaRegistryUrl()
        + ", persistentRegistry="
        + this.getPersistentRegistry()
        + ", deleteTransformationsStaleForDays="
        + this.getDeleteTransformationsStaleForDays()
        + ", deleteSnapshotStaleForDays="
        + this.getDeleteSnapshotStaleForDays()
        + ", persistentTransformationCache="
        + this.getPersistentTransformationCache()
        + ", inMemTransformationCacheCapacity="
        + this.getInMemTransformationCacheCapacity()
        + ", allowUnvalidatedPublish="
        + this.getAllowUnvalidatedPublish()
        + ", allowSchemaReplace="
        + this.getAllowSchemaReplace()
        + ", factNotificationBlockingWaitTimeInMillis="
        + this.getFactNotificationBlockingWaitTimeInMillis()
        + ", factNotificationMaxRoundTripLatencyInMillis="
        + this.getFactNotificationMaxRoundTripLatencyInMillis()
        + ", factNotificationNewConnectionWaitTimeInMillis="
        + this.getFactNotificationNewConnectionWaitTimeInMillis()
        + ", integrationTestMode="
        + this.getIntegrationTestMode()
        + ", tailIndexingEnabled="
        + this.getTailIndexingEnabled()
        + ", tailGenerationsToKeep="
        + this.getTailGenerationsToKeep()
        + ", minimumTailAge="
        + this.getMinimumTailAge()
        + ")";
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
}
