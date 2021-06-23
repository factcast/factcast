package org.factcast.store.pgsql.registry;

import lombok.NonNull;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;

public class SomeSchemaRegistry extends AbstractSchemaRegistry {
  public SomeSchemaRegistry(
      @NonNull IndexFetcher indexFetcher,
      @NonNull RegistryFileFetcher registryFileFetcher,
      @NonNull SchemaStore schemaStore,
      @NonNull TransformationStore transformationStore,
      @NonNull RegistryMetrics registryMetrics,
      @NonNull PgConfigurationProperties pgConfigurationProperties) {
    super(
        indexFetcher,
        registryFileFetcher,
        schemaStore,
        transformationStore,
        registryMetrics,
        pgConfigurationProperties);
  }
}
