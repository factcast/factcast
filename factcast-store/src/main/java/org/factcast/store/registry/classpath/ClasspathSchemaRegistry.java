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
package org.factcast.store.registry.classpath;

import lombok.NonNull;
import net.javacrumbs.shedlock.core.LockProvider;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.AbstractSchemaRegistry;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.schema.SchemaStore;

public class ClasspathSchemaRegistry extends AbstractSchemaRegistry {

  public ClasspathSchemaRegistry(
      @NonNull String base,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties storeConfigurationProperties,
      @NonNull LockProvider lockProvider) {
    super(
        new ClasspathIndexFetcher(base),
        new ClasspathRegistryFileFetcher(base),
        schemaStore,
        transformationStore,
        registryMetrics,
        storeConfigurationProperties,
        lockProvider);
  }

  @Override
  public void refresh() {
    // does not need to be refreshed.
  }
}
