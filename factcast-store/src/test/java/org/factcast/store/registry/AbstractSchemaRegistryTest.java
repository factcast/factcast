package org.factcast.store.registry;

import com.github.fge.jsonschema.main.JsonSchema;
import com.google.common.cache.LoadingCache;
import lombok.NonNull;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AbstractSchemaRegistryTest {
  @Mock private @NonNull IndexFetcher indexFetcher;
  @Mock private @NonNull RegistryFileFetcher registryFileFetcher;
  @Mock private @NonNull SchemaStore schemaStore;
  @Mock private @NonNull TransformationStore transformationStore;
  @Mock private @NonNull RegistryMetrics registryMetrics;
  @Mock private @NonNull StoreConfigurationProperties pgConfigurationProperties;
  @Mock private Object mutex;
  @Mock private LoadingCache<SchemaKey, JsonSchema> cache;
  @InjectMocks private SomeSchemaRegistry underTest;

  @Nested
  class WhenFetchingInitial {
    @BeforeEach
    void setup() {}

    @Test
    void justCountsWhenSchemaPersistent() {
      when(indexFetcher.fetchIndex()).thenThrow(IllegalStateException.class);
      when(pgConfigurationProperties.isPersistentRegistry()).thenReturn(true);

      underTest.fetchInitial();
      verify(registryMetrics).count(EVENT.SCHEMA_UPDATE_FAILURE);
    }

    @Test
    void throwsWhenSchemaIsNotPersistent() {
      when(indexFetcher.fetchIndex()).thenThrow(IllegalStateException.class);
      when(pgConfigurationProperties.isPersistentRegistry()).thenReturn(false);

      assertThatThrownBy(
              () -> {
                underTest.fetchInitial();
              })
          .isInstanceOf(InitialRegistryFetchFailed.class);

      verify(registryMetrics).count(EVENT.SCHEMA_UPDATE_FAILURE);
    }
  }
}
