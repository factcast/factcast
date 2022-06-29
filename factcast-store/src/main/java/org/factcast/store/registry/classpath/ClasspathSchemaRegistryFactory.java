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

import com.google.common.collect.Lists;
import java.util.List;
import lombok.NonNull;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.SchemaRegistryFactory;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.schema.SchemaStore;

public class ClasspathSchemaRegistryFactory
    implements SchemaRegistryFactory<ClasspathSchemaRegistry> {

  @Override
  public List<String> getProtocols() {
    return Lists.newArrayList("classpath");
  }

  @Override
  public ClasspathSchemaRegistry createInstance(
      @NonNull String fullUrl,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties props) {

    return new ClasspathSchemaRegistry(
        fullUrl.substring("classpath:".length()),
        schemaStore,
        transformationStore,
        registryMetrics,
        props);
  }
}
