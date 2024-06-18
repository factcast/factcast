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
package org.factcast.store.registry.http;

import com.google.common.annotations.VisibleForTesting;
import java.net.URL;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockProvider;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.AbstractSchemaRegistry;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.schema.SchemaStore;

@Slf4j
public class HttpSchemaRegistry extends AbstractSchemaRegistry {

  public HttpSchemaRegistry(
      @NonNull URL baseUrl,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties storeConfigurationProperties,
      @NonNull LockProvider lockProvider) {
    this(
        schemaStore,
        transformationStore,
        new HttpIndexFetcher(baseUrl, registryMetrics),
        new HttpRegistryFileFetcher(baseUrl, registryMetrics),
        registryMetrics,
        storeConfigurationProperties,
        lockProvider);
  }

  @VisibleForTesting
  protected HttpSchemaRegistry(
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull HttpIndexFetcher indexFetcher,
      @NonNull HttpRegistryFileFetcher registryFileFetcher,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties storeConfigurationProperties,
      @NonNull LockProvider lockProvider) {
    super(
        indexFetcher,
        registryFileFetcher,
        schemaStore,
        transformationStore,
        registryMetrics,
        storeConfigurationProperties,
        lockProvider);
  }
}
