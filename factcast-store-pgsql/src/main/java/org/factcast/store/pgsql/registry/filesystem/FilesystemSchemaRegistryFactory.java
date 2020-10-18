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

import com.google.common.collect.Lists;
import java.util.List;
import lombok.NonNull;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.SchemaRegistryFactory;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;

public class FilesystemSchemaRegistryFactory
    implements SchemaRegistryFactory<FilesystemSchemaRegistry> {

  @Override
  public List<String> getProtocols() {
    return Lists.newArrayList("file");
  }

  @Override
  public FilesystemSchemaRegistry createInstance(
      @NonNull String fullUrl,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull PgConfigurationProperties props) {

    return new FilesystemSchemaRegistry(
        fullUrl.substring("file://".length()),
        schemaStore,
        transformationStore,
        registryMetrics,
        props);
  }
}
