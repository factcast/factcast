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
package org.factcast.store.pgsql;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.*;

@SuppressWarnings("DefaultAnnotationParam")
@ConfigurationProperties(prefix = PgConfigurationProperties.PROPERTIES_PREFIX)
@Data
@Slf4j
@Accessors(fluent = false)
public class PgConfigurationProperties
    implements ApplicationListener<ApplicationReadyEvent>, InitializingBean {
  private static final String LEGACY_PREFIX = "factcast.pg";

  public static final String PROPERTIES_PREFIX = "factcast.store.pgsql";

  @Autowired Environment env;

  @Autowired private ApplicationContext appContext;

  /**
   * defines the number of Facts being retrieved with one Page Query for PageStrategy.PAGED, or
   * respectively the fetchSize when using PageStrategy.FETCHING
   */
  int pageSize = 50;
  /** Defines the Strategy used for Paging in the Catchup Phase. */
  CatchupStrategy catchupStrategy = CatchupStrategy.getDefault();

  /**
   * Optional URL to a Schema Registry. If this is null, validation will be disabled and a warning
   * will be issued. (Defaults to null) Currently a String type due to the fact that "classpath:" is
   * a spring-only protocol
   */
  String schemaRegistryUrl;

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
  int deleteTransformationsStaleForDays = 14;

  /**
   * this is the min number of days a snapshot is not read in order to be considered stale. This
   * should free some space in a regular cleanup job
   */
  int deleteSnapshotStaleForDays = 90;

  /**
   * If validation is enabled, this controls if transformed facts are persistently cached in
   * postgres, rather than in memory. (Defaults to false)
   */
  boolean persistentTransformationCache = false;

  /**
   * when using the inmem impl of the transformation cache, this is the max number of entries
   * cached.
   */
  int inMemTransformationCacheCapacity = 1_000_000;

  /**
   * If validation is enabled, this controls if publishing facts, that are not validatable (due to
   * missing meta-data or due to missing schema in the registry) are allowed to be published or
   * should be rejected. (Defaults to false)
   */
  boolean allowUnvalidatedPublish = false;

  /**
   * If a schema can be replaced by an updated version from the registry (not a good idea in
   * production environments)
   */
  boolean allowSchemaReplace = false;

  /**
   * Controls how long to block waiting for new notifications from the database (Postgres LISTEN/
   * NOTIFY mechanism). When this time exceeds the health of the database connection is checked.
   * After that waiting for new notifications is repeated.
   */
  int factNotificationBlockingWaitTimeInMillis = 1000 * 15;

  /**
   * When Factcast did not receive any notifications after factNotificationBlockingWaitTimeInMillis
   * milliseconds it validates the health of the database connection. For this purpose it sends an
   * internal notification to the database and waits for the given time to receive back an answer.
   *
   * <p>If the time is exceeded the database connection is renewed.
   */
  int factNotificationMaxRoundTripLatencyInMillis = 200;

  /**
   * How much time to wait between invalidating and acquiring a new connection. Note: This parameter
   * is only applied in the part of Factcast which deals with receiving and forwarding database
   * notifications.
   */
  int factNotificationNewConnectionWaitTimeInMillis = 100;

  /**
   * If this is set to true, all process-internal caches are bypassed (unless they are essential,
   * like schemareg). That makes it possible to wipe the database between integration tests in order
   * to prevent side-effects.
   */
  boolean integrationTestMode = false;

  /** tail indexing feature state */
  boolean tailIndexingEnabled = true;

  /**
   * the number of tail indexes to keep. The higher the number, the slower the inserts. Probably 2
   * or 3 is a good value unless you have a very high tail rebuild frequency and not permanently
   * connected applications (like offline clients for instance)
   */
  int tailGenerationsToKeep = 3;

  /**
   * Minimum tail age. Tail rotation will be skipped, unless the age of the youngest existing tail
   * is at least this old. Defaults to 7 days
   */
  Duration minimumTailAgeInDays = Duration.ofDays(7);

  @Override
  public void onApplicationEvent(@Nonnull ApplicationReadyEvent event) {
    List<Entry<String, Object>> legacyProperties =
        findAllProperties().entrySet().stream()
            .filter(e -> e.getKey().startsWith(LEGACY_PREFIX))
            .collect(Collectors.toList());

    if (!legacyProperties.isEmpty()) {
      log.error(
          "There are legacy properties detected. Property namespace has been renamed from '"
              + LEGACY_PREFIX
              + "' to 'factcast.store.pgsql'");
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

  public boolean isValidationEnabled() {
    return schemaRegistryUrl != null;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    if (integrationTestMode) {
      log.warn(
          "**** You are running in INTEGRATION TEST MODE. If you see this in production, "
              + "this would be a good time to panic. (See "
              + PROPERTIES_PREFIX
              + ".integrationTestMode) ****");
    }
  }
}
