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
package org.factcast.store.pgsql.registry.filesystem;

import lombok.NonNull;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.AbstractSchemaRegistry;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;

public class FilesystemSchemaRegistry extends AbstractSchemaRegistry {

  public FilesystemSchemaRegistry(
      @NonNull String base,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull PgConfigurationProperties pgConfigurationProperties) {
    super(
        new FilesystemIndexFetcher(base),
        new FilesystemRegistryFileFetcher(base),
        schemaStore,
        transformationStore,
        registryMetrics,
        pgConfigurationProperties);
  }
}
