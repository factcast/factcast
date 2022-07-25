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
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.transformation.TransformationRequest;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.registry.NOPRegistryMetrics;
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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactTransformerServiceImplTest {
  @Mock private @NonNull TransformationChains chains;
  @Mock private @NonNull Transformer trans;
  @Mock private @NonNull TransformationCache cache;
  @Spy private @NonNull RegistryMetrics registryMetrics = new NOPRegistryMetrics();
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
      when(req.targetVersions()).thenReturn(Collections.singleton(0));
      when(req.toTransform()).thenReturn(fact);
      Fact transformed = underTest.transform(req);
      assertThat(transformed).isSameAs(req.toTransform());
    }

    @Test
    void noChangeWhenSameTargetVersion() {
      when(req.targetVersions()).thenReturn(Collections.singleton(4));
      when(req.toTransform()).thenReturn(fact);
      when(fact.version()).thenReturn(4);
      Fact transformed = underTest.transform(req);
      assertThat(transformed).isSameAs(req.toTransform());
    }

    @Test
    void shouldBeParallelOnlyOneRequest() {
      Transformation tf1 = mock(Transformation.class);
      TransformationKey key1 = TransformationKey.of("foo", "bar");
      when(tf1.key()).thenReturn(key1);

      chain = TransformationChain.of(key1, Lists.newArrayList(tf1), "[1,2,3]");

      List<TransformationChain> chains = Lists.newArrayList(chain);
      assertThat(underTest.shouldBeParallel(chains.stream())).isFalse();
    }

    @Test
    void shouldBeParallelSameChain() {
      Transformation tf1 = mock(Transformation.class);
      TransformationKey key1 = TransformationKey.of("foo", "bar");
      when(tf1.key()).thenReturn(key1);

      chain = TransformationChain.of(key1, Lists.newArrayList(tf1), "[1,2,3]");

      List<TransformationChain> chains = Lists.newArrayList(chain, chain);

      assertThat(underTest.shouldBeParallel(chains.stream())).isFalse();
    }

    @Test
    void shouldBeParallelDifferentKey() {
      Transformation tf1 = mock(Transformation.class);
      TransformationKey key1 = TransformationKey.of("foo", "bar");
      when(tf1.key()).thenReturn(key1);

      Transformation tf2 = mock(Transformation.class);
      TransformationKey key2 = TransformationKey.of("foo", "baz");
      when(tf2.key()).thenReturn(key2);

      var chain1 = TransformationChain.of(key1, Lists.newArrayList(tf1), "[1,2,3]");
      var chain2 = TransformationChain.of(key2, Lists.newArrayList(tf2), "[1,2,3]");

      List<TransformationChain> chains = Lists.newArrayList(chain1, chain2);

      assertThat(underTest.shouldBeParallel(chains.stream())).isTrue();
    }

    @Test
    void shouldBeParallelDifferentId() {
      Transformation tf1 = mock(Transformation.class);
      TransformationKey key1 = TransformationKey.of("foo", "bar");
      when(tf1.key()).thenReturn(key1);
      var chain1 = TransformationChain.of(key1, Lists.newArrayList(tf1), "[1,2,3]");
      var chain2 = TransformationChain.of(key1, Lists.newArrayList(tf1), "[1,3]");

      List<TransformationChain> chains = Lists.newArrayList(chain1, chain2);

      assertThat(underTest.shouldBeParallel(chains.stream())).isTrue();
    }

    @Test
    void returnsCached() {
      when(fact.version()).thenReturn(4);
      when(fact.ns()).thenReturn("ns");
      when(fact.type()).thenReturn("type");
      when(fact.id()).thenReturn(UUID.randomUUID());

      TransformationKey key = TransformationKey.from(fact);

      when(req.targetVersions()).thenReturn(Collections.singleton(5));
      when(req.toTransform()).thenReturn(fact);

      when(chain.id()).thenReturn("myChainId");
      when(chain.toVersion()).thenReturn(5);
      when(chains.get(eq(key), eq(4), eq(Collections.singleton(5)))).thenReturn(chain);
      TransformationCache.Key cacheKey = TransformationCache.Key.of(fact.id(), 5, "myChainId");
      when(cache.find(cacheKey)).thenReturn(Optional.of(fact2));

      Fact transformed = underTest.transform(req);
      assertThat(transformed).isSameAs(fact2);
    }
  }

  @Nested
  class WhenDoingTransform {
    @Mock private @NonNull Fact fact;
    @Mock private @NonNull Fact fact2;

    @Mock private @NonNull TransformationChain chain;
    @Mock private @NonNull TransformationRequest req;

    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void throwsException() {

      fact =
          Fact.builder().version(4).ns("ns").type("type").id(UUID.randomUUID()).build("{\"a\":1}");

      TransformationKey key = TransformationKey.from(fact);

      when(req.targetVersions()).thenReturn(Collections.singleton(5));
      when(req.toTransform()).thenReturn(fact);

      when(chain.id()).thenReturn("myChainId");
      when(chain.toVersion()).thenReturn(5);
      when(chains.get(eq(key), eq(4), eq(Collections.singleton(5)))).thenReturn(chain);
      TransformationCache.Key cacheKey = TransformationCache.Key.of(fact.id(), 5, "myChainId");
      when(cache.find(cacheKey)).thenReturn(Optional.empty());
      Transformation t;
      when(trans.transform(same(chain), eq(FactCastJson.readTree(fact.jsonPayload()))))
          .thenThrow(TransformationException.class);

      assertThatThrownBy(
              () -> {
                underTest.transform(req);
              })
          .isInstanceOf(TransformationException.class);
    }

    @SneakyThrows
    @Test
    void transforms() {

      fact =
          Fact.builder().version(4).ns("ns").type("type").id(UUID.randomUUID()).build("{\"a\":1}");

      TransformationKey key = TransformationKey.from(fact);

      when(req.targetVersions()).thenReturn(Collections.singleton(5));
      when(req.toTransform()).thenReturn(fact);

      when(chain.id()).thenReturn("myChainId");
      when(chain.toVersion()).thenReturn(5);
      when(chains.get(eq(key), eq(4), eq(Collections.singleton(5)))).thenReturn(chain);
      TransformationCache.Key cacheKey = TransformationCache.Key.of(fact.id(), 5, "myChainId");
      when(cache.find(cacheKey)).thenReturn(Optional.empty());
      Transformation t;
      when(trans.transform(same(chain), eq(FactCastJson.readTree(fact.jsonPayload()))))
          .thenReturn(FactCastJson.readTree("{\"a\":2}"));

      Fact transformed = underTest.transform(req);
      assertThat(transformed.jsonPayload()).isEqualTo("{\"a\":2}");
    }

    @SneakyThrows
    @Test
    void transformsList() {

      fact =
          Fact.builder().version(4).ns("ns").type("type").id(UUID.randomUUID()).build("{\"a\":1}");

      TransformationKey key = TransformationKey.from(fact);

      when(req.targetVersions()).thenReturn(Collections.singleton(5));
      when(req.toTransform()).thenReturn(fact);

      when(chain.id()).thenReturn("myChainId");
      when(chain.key()).thenReturn(TransformationKey.of("a", "b"));
      when(chain.toVersion()).thenReturn(5);
      when(chains.get(eq(key), eq(4), eq(Collections.singleton(5)))).thenReturn(chain);
      TransformationCache.Key cacheKey = TransformationCache.Key.of(fact.id(), 5, "myChainId");
      when(cache.findAll(eq(Lists.newArrayList(cacheKey)))).thenReturn(Collections.emptySet());
      Transformation t;
      when(trans.transform(same(chain), eq(FactCastJson.readTree(fact.jsonPayload()))))
          .thenReturn(FactCastJson.readTree("{\"a\":2}"));

      List<Fact> transformed = underTest.transform(Lists.newArrayList(req));
      assertThat(transformed.get(0).jsonPayload()).isEqualTo("{\"a\":2}");
      assertThat(transformed).hasSize(1);
    }

    @SneakyThrows
    @Test
    void transformsListAndMixesInCacheResults() {

      fact =
          Fact.builder().version(4).ns("ns").type("type1").id(UUID.randomUUID()).build("{\"a\":1}");
      fact2 =
          Fact.builder().version(4).ns("ns").type("type2").id(UUID.randomUUID()).build("{\"a\":2}");

      Fact fact2transformed =
          Fact.builder().version(5).ns("ns").type("type2").id(fact2.id()).build("{\"a\":3}");

      TransformationKey key1 = TransformationKey.from(fact);
      TransformationKey key2 = TransformationKey.from(fact2);

      var req1 = new TransformationRequest(fact, Collections.singleton(5));
      var req2 = new TransformationRequest(fact2, Collections.singleton(5));

      when(chain.id()).thenReturn("chain1");
      when(chain.key()).thenReturn(TransformationKey.of("a", "b"));
      when(chains.get(eq(key1), eq(4), eq(Collections.singleton(5)))).thenReturn(chain);
      when(chains.get(eq(key2), eq(4), eq(Collections.singleton(5)))).thenReturn(chain);

      when(cache.findAll(any())).thenReturn(Sets.newHashSet(fact2transformed));

      when(trans.transform(same(chain), eq(FactCastJson.readTree(fact.jsonPayload()))))
          .thenReturn(FactCastJson.readTree("{\"a\":2}"));

      List<Fact> transformed = underTest.transform(Lists.newArrayList(req1, req2));
      assertThat(transformed.get(0).type()).isEqualTo("type1");
      assertThat(transformed.get(0).jsonPayload()).isEqualTo("{\"a\":2}");
      assertThat(transformed.get(1).type()).isEqualTo("type2");
      assertThat(transformed.get(1).jsonPayload()).isEqualTo("{\"a\":3}");

      assertThat(transformed).hasSize(2);
    }
  }
}
