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

import lombok.Data;
import lombok.Getter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
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
@Data
@Getter(onMethod_ = {@DeprecatedConfigurationProperty(reason = "Use new prefix 'factcast.store'.")})
@Slf4j
@Accessors(fluent = false)
public class PgConfigurationLegacyProperties implements ApplicationListener<ApplicationReadyEvent> {

  public static final String LEGACY_PREFIX = "factcast.store.pgsql";

  @Autowired private Environment env;

  /**
   * defines the number of Facts being retrieved with one Page Query for PageStrategy.PAGED, or
   * respectively the fetchSize when using PageStrategy.FETCHING
   */
  Optional<Integer> pageSize = Optional.empty();
  /** Defines the Strategy used for Paging in the Catchup Phase. */
  Optional<CatchupStrategy> catchupStrategy = Optional.empty();

  /**
   * Optional URL to a Schema Registry. If this is null, validation will be disabled and a warning
   * will be issued. (Defaults to null) Currently a String type due to the fact that "classpath:" is
   * a spring-only protocol
   */
  Optional<String> schemaRegistryUrl = Optional.empty();

  /**
   * If validation is enabled, this controls if the local snapshot of the registry is persisted to
   * psql or just kept in mem. (Defaults to true)
   */
  Optional<Boolean> persistentRegistry = Optional.empty();

  /**
   * when using the persistent impl of the transformation cache, this is the min number of days a
   * transformation result is not read in order to be considered stale. This should free some space
   * in a regular cleanup job
   */
  Optional<Integer> deleteTransformationsStaleForDays = Optional.empty();

  /**
   * this is the min number of days a snapshot is not read in order to be considered stale. This
   * should free some space in a regular cleanup job
   */
  Optional<Integer> deleteSnapshotStaleForDays = Optional.empty();

  /**
   * If validation is enabled, this controls if transformed facts are persistently cached in
   * postgres, rather than in memory. (Defaults to false)
   */
  Optional<Boolean> persistentTransformationCache = Optional.empty();

  /**
   * when using the inmem impl of the transformation cache, this is the max number of entries
   * cached.
   */
  Optional<Integer> inMemTransformationCacheCapacity = Optional.empty();

  /**
   * If validation is enabled, this controls if publishing facts, that are not validatable (due to
   * missing meta-data or due to missing schema in the registry) are allowed to be published or
   * should be rejected. (Defaults to false)
   */
  Optional<Boolean> allowUnvalidatedPublish = Optional.empty();

  /**
   * If a schema can be replaced by an updated version from the registry (not a good idea in
   * production environments)
   */
  Optional<Boolean> allowSchemaReplace = Optional.empty();

  /**
   * Controls how long to block waiting for new notifications from the database (Postgres LISTEN/
   * NOTIFY mechanism). When this time exceeds the health of the database connection is checked.
   * After that waiting for new notifications is repeated.
   */
  Optional<Integer> factNotificationBlockingWaitTimeInMillis = Optional.empty();

  /**
   * When Factcast did not receive any notifications after factNotificationBlockingWaitTimeInMillis
   * milliseconds it validates the health of the database connection. For this purpose it sends an
   * internal notification to the database and waits for the given time to receive back an answer.
   *
   * <p>If the time is exceeded the database connection is renewed.
   */
  Optional<Integer> factNotificationMaxRoundTripLatencyInMillis = Optional.empty();

  /**
   * How much time to wait between invalidating and acquiring a new connection. Note: This parameter
   * is only applied in the part of Factcast which deals with receiving and forwarding database
   * notifications.
   */
  Optional<Integer> factNotificationNewConnectionWaitTimeInMillis = Optional.empty();

  /**
   * If this is set to true, all process-internal caches are bypassed (unless they are essential,
   * like schemareg). That makes it possible to wipe the database between integration tests in order
   * to prevent side-effects.
   */
  Optional<Boolean> integrationTestMode = Optional.empty();

  /** tail indexing feature state */
  Optional<Boolean> tailIndexingEnabled = Optional.empty();

  /**
   * the number of tail indexes to keep. The higher the number, the slower the inserts. Probably 2
   * or 3 is a good value unless you have a very high tail rebuild frequency and not permanently
   * connected applications (like offline clients for instance)
   */
  Optional<Integer> tailGenerationsToKeep = Optional.empty();

  /**
   * Minimum tail age. Tail rotation will be skipped, unless the age of the youngest existing tail
   * is at least this old. Defaults to 7 days
   */
  Optional<Duration> minimumTailAge = Optional.empty();

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
}
