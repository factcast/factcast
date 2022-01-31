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
package org.factcast.store.registry;

import io.micrometer.core.instrument.MeterRegistry;
import java.net.MalformedURLException;
import java.util.List;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.classpath.ClasspathSchemaRegistryFactory;
import org.factcast.store.registry.filesystem.FilesystemSchemaRegistryFactory;
import org.factcast.store.registry.http.HttpSchemaRegistryFactory;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetricsImpl;
import org.factcast.store.registry.transformation.TransformationConfiguration;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.FactValidatorConfiguration;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@Configuration
@EnableScheduling
@Import({FactValidatorConfiguration.class, TransformationConfiguration.class})
public class SchemaRegistryConfiguration {

  @Bean
  public RegistryMetrics registryMetrics(MeterRegistry meterRegistry) {
    return new RegistryMetricsImpl(meterRegistry);
  }

  @Bean
  public FilesystemSchemaRegistryFactory filesystemSchemaRegistryFactory() {
    return new FilesystemSchemaRegistryFactory();
  }

  @Bean
  public ClasspathSchemaRegistryFactory classpathSchemaRegistryFactory() {
    return new ClasspathSchemaRegistryFactory();
  }

  @Bean
  public HttpSchemaRegistryFactory httpSchemaRegistryFactory() {
    return new HttpSchemaRegistryFactory();
  }

  @Bean
  public SchemaRegistry schemaRegistry(
      StoreConfigurationProperties p,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull List<SchemaRegistryFactory<? extends SchemaRegistry>> factories,
      @NonNull RegistryMetrics registryMetrics) {

    try {

      if (p.isSchemaRegistryConfigured()) {
        String fullUrl = p.getSchemaRegistryUrl();
        if (!fullUrl.contains(":")) {
          fullUrl = "classpath:" + fullUrl;
        }

        String protocol = fullUrl.substring(0, fullUrl.indexOf(":"));

        SchemaRegistryFactory<? extends SchemaRegistry> registryFactory =
            getSchemaRegistryFactory(factories, protocol);

        SchemaRegistry registry =
            registryFactory.createInstance(
                fullUrl, schemaStore, transformationStore, registryMetrics, p);

        registry.fetchInitial();

        return registry;

      } else {
        return new NOPSchemaRegistry();
      }

    } catch (MalformedURLException e) {
      throw new SchemaRegistryUnavailableException(e);
    }
  }

  private SchemaRegistryFactory<? extends SchemaRegistry> getSchemaRegistryFactory(
      @NonNull List<SchemaRegistryFactory<? extends SchemaRegistry>> factories, String protocol) {

    return factories.stream()
        .filter(f -> f.canHandle(protocol))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "schemaRegistryUrl has an unknown protocol: '"
                        + protocol
                        + "'. Allowed protocols: "
                        + getProtocols(factories)));
  }

  private String getProtocols(List<SchemaRegistryFactory<? extends SchemaRegistry>> factories) {
    return factories.stream()
        .map(SchemaRegistryFactory::getProtocols)
        .flatMap(List::stream)
        .map(p -> "'" + p + "'")
        .collect(Collectors.joining(", "));
  }

  @Bean
  public ScheduledRegistryRefresher scheduledRegistryFresher(
      SchemaRegistry registry, StoreConfigurationProperties properties) {
    if (properties.isSchemaRegistryConfigured()) {
      return new ScheduledRegistryRefresher(registry);
    } else return null;
  }
}
