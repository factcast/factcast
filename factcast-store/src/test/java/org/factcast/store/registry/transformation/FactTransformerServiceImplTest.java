/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.registry.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactTransformerServiceImplTest {
  @Mock private @NonNull TransformationChains chains;
  @Mock private @NonNull Transformer trans;
  @Mock private @NonNull TransformationCache cache;
  @Mock private @NonNull RegistryMetrics registryMetrics;
  @InjectMocks private FactTransformerServiceImpl underTest;

  @Nested
  class WhenTransforming {
    @Mock private @NonNull TransformationRequest req;
    @Mock private @NonNull Fact fact;
    @Mock private @NonNull Fact fact2;
    @Mock private @NonNull TransformationChain chain;

    @BeforeEach
    void setup() {}

    @Test
    void noChangeWhenNoTargetVersion() {
      when(req.targetVersion()).thenReturn(0);
      when(req.toTransform()).thenReturn(fact);
      Fact transformed = underTest.transform(req);
      assertThat(transformed).isSameAs(req.toTransform());
    }

    @Test
    void noChangeWhenSameTargetVersion() {
      when(req.targetVersion()).thenReturn(4);
      when(req.toTransform()).thenReturn(fact);
      when(fact.version()).thenReturn(4);
      Fact transformed = underTest.transform(req);
      assertThat(transformed).isSameAs(req.toTransform());
    }

    @Test
    void returnsCached() {
      when(fact.version()).thenReturn(4);
      when(fact.ns()).thenReturn("ns");
      when(fact.type()).thenReturn("type");
      when(fact.id()).thenReturn(UUID.randomUUID());

      TransformationKey key = TransformationKey.from(fact);

      when(req.targetVersion()).thenReturn(5);
      when(req.toTransform()).thenReturn(fact);

      when(chain.id()).thenReturn("myChainId");
      when(chains.get(eq(key), eq(4), eq(5))).thenReturn(chain);
      TransformationCache.Key cacheKey = TransformationCache.Key.of(fact.id(), 5, "myChainId");
      when(cache.find(cacheKey)).thenReturn(Optional.of(fact2));

      Fact transformed = underTest.transform(req);
      assertThat(transformed).isSameAs(fact2);
    }
  }

  @Nested
  class WhenDoingTransform {
    @Mock private @NonNull Fact e;
    @Mock private @NonNull TransformationChain chain;

    @BeforeEach
    void setup() {}
  }
}
