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
package org.factcast.store.pgsql.registry.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.MalformedURLException;
import lombok.NonNull;
import org.factcast.store.pgsql.PgConfigurationProperties;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.transformation.TransformationStore;
import org.factcast.store.pgsql.registry.validation.schema.SchemaStore;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HttpSchemaRegistryFactoryTest {

  @InjectMocks private HttpSchemaRegistryFactory underTest;

  @Test
  void getProtocols() {
    assertThat(underTest.getProtocols()).containsExactly("http", "https");
  }

  @Nested
  class WhenCreatingInstance {
    private final String FULL_URL = "https://xxx";

    @Mock private @NonNull SchemaStore schemaStore;

    @Mock private @NonNull TransformationStore transformationStore;

    @Mock private @NonNull RegistryMetrics registryMetrics;

    @Mock private @NonNull PgConfigurationProperties props;

    @Test
    void createInstance() throws MalformedURLException {
      underTest.createInstance(FULL_URL, schemaStore, transformationStore, registryMetrics, props);
    }
  }
}
