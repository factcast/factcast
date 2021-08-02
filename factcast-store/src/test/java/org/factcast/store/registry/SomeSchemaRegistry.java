package org.factcast.store.registry;

import lombok.NonNull;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.schema.SchemaStore;

public class SomeSchemaRegistry extends AbstractSchemaRegistry {
  public SomeSchemaRegistry(
      @NonNull IndexFetcher indexFetcher,
      @NonNull RegistryFileFetcher registryFileFetcher,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull StoreConfigurationProperties pgConfigurationProperties) {
    super(
        indexFetcher,
        registryFileFetcher,
        schemaStore,
        transformationStore,
        registryMetrics,
        pgConfigurationProperties);
  }
}
