package org.factcast.store.registry.transformation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.factcast.core.Fact;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.RequestedVersions;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.cache.FactWithTargetVersion;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.factcast.store.registry.transformation.chains.Transformer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;

import lombok.SneakyThrows;

@ExtendWith(MockitoExtension.class)
class BatchCacheLookupFactTransformerServiceTest {

  @Mock TransformationCache cache;
  @Mock TransformationChains chains;
  @Mock Transformer trans;
  @Mock RegistryMetrics registryMetrics;

  // cannot inject mocks, as the querying of the cache happens already async in the constructor.
  // create in setup method after mocks got initialised
  BatchCacheLookupFactTransformerService underTest;

  final List<Fact> factsForCacheWarmup = new ArrayList<>();
  final RequestedVersions requestedVersions = new RequestedVersions();

  @Nested
  class NonEmptyListOfFacts {

    @Mock TransformationChain chain;

    final UUID factIdNotRequiringTransformation = UUID.randomUUID();
    final UUID factIdRequiringTransformation_foundInCache = UUID.randomUUID();
    final UUID factIdRequiringTransformation_notFoundInCache = UUID.randomUUID();

    Fact factNotRequiringTransformation;
    Fact factRequiringTransformation_foundInCache;
    Fact factRequiringTransformation_notFoundInCache;

    Fact transformedFact_foundInCache;

    final TransformationKey key = TransformationKey.of("ns", "type");

    @BeforeEach
    @SneakyThrows
    void setup() {

      factNotRequiringTransformation =
          Fact.builder()
              .ns("ns")
              .type("type")
              .id(factIdNotRequiringTransformation)
              .version(2)
              .build("{\"y\":0}");

      factRequiringTransformation_foundInCache =
          Fact.builder()
              .ns("ns")
              .type("type")
              .id(factIdRequiringTransformation_foundInCache)
              .version(1)
              .build("{\"x\":1}");

      factRequiringTransformation_notFoundInCache =
          Fact.builder()
              .ns("ns")
              .type("type")
              .id(factIdRequiringTransformation_notFoundInCache)
              .version(1)
              .build("{\"x\":2}");

      factsForCacheWarmup.add(factNotRequiringTransformation);
      factsForCacheWarmup.add(factRequiringTransformation_foundInCache);
      factsForCacheWarmup.add(factRequiringTransformation_notFoundInCache);

      requestedVersions.add("ns", "type", 2);

      when(chains.get(key, 1, 2)).thenReturn(chain);

      var factFoundInCacheWithTargetVersion =
          new FactWithTargetVersion(1, factRequiringTransformation_foundInCache, 2, key, chain);
      var factNotFoundInCacheWithTargetVersion =
          new FactWithTargetVersion(2, factRequiringTransformation_notFoundInCache, 2, key, chain);

      transformedFact_foundInCache =
          Fact.builder()
              .ns("ns")
              .type("type")
              .id(factIdRequiringTransformation_foundInCache)
              .version(2)
              .build("{\"y\":1}");

      Map<FactWithTargetVersion, Fact> cachedFacts = new HashMap<>();
      cachedFacts.put(factFoundInCacheWithTargetVersion, transformedFact_foundInCache);

      when(cache.find(any())).thenReturn(cachedFacts);

      when(trans.transform(chain, FactCastJson.readTree("{\"x\":2}")))
          .thenReturn(FactCastJson.readTree("{\"y\":2}"));

      underTest =
          new BatchCacheLookupFactTransformerService(
              chains, trans, cache, registryMetrics, factsForCacheWarmup, requestedVersions);
    }

    @Captor ArgumentCaptor<Collection<FactWithTargetVersion>> factsCaptor;

    @Test
    void transformIfNecessary() {

      assertThat(underTest.transformIfNecessary(factNotRequiringTransformation, 2))
          .isEqualTo(factNotRequiringTransformation);

      assertThat(underTest.transformIfNecessary(factRequiringTransformation_foundInCache, 2))
          .isEqualTo(transformedFact_foundInCache);

      assertThat(underTest.transformIfNecessary(factRequiringTransformation_notFoundInCache, 2))
          .extracting(Fact::id, Fact::ns, Fact::type, Fact::version, Fact::jsonPayload)
          .contains(factIdRequiringTransformation_notFoundInCache, "ns", "type", 2, "{\"y\":2}");

      verify(cache).find(factsCaptor.capture());

      assertThat(factsCaptor.getValue())
          .extracting(
              FactWithTargetVersion::order,
              FactWithTargetVersion::fact,
              FactWithTargetVersion::targetVersion,
              FactWithTargetVersion::transformationKey,
              FactWithTargetVersion::transformationChain)
          .containsExactlyInAnyOrder(
              tuple(1, factRequiringTransformation_foundInCache, 2, key, chain),
              tuple(2, factRequiringTransformation_notFoundInCache, 2, key, chain));
    }
  }

  @Nested
  class WithException {

    @Mock TransformationChain chain;

    final UUID factId = UUID.randomUUID();

    Fact fact;

    final TransformationKey key = TransformationKey.of("ns", "type");

    @BeforeEach
    @SneakyThrows
    void setup() {

      fact = Fact.builder().ns("ns").type("type").id(factId).version(1).build("{\"y\":0}");

      factsForCacheWarmup.add(fact);

      requestedVersions.add("ns", "type", 2);
      when(chains.get(key, 1, 2)).thenReturn(chain);

      when(cache.find(any())).thenThrow(new QueryTimeoutException("Error"));

      underTest =
          new BatchCacheLookupFactTransformerService(
              chains, trans, cache, registryMetrics, factsForCacheWarmup, requestedVersions);
    }

    @Test
    void transformIfNecessary() {

      assertThatThrownBy(() -> underTest.transformIfNecessary(fact, 2))
          .isInstanceOf(TransformationException.class)
          .getCause()
          .isInstanceOf(ExecutionException.class)
          .getCause()
          .isInstanceOf(QueryTimeoutException.class);

      verify(cache).find(any());
      verifyNoMoreInteractions(cache);

      verifyNoInteractions(trans);
    }
  }

  @Nested
  class EmptyListOfFacts {

    final UUID factId = UUID.randomUUID();

    @BeforeEach
    @SneakyThrows
    void setup() {
      underTest =
          new BatchCacheLookupFactTransformerService(
              chains, trans, cache, registryMetrics, factsForCacheWarmup, requestedVersions);
    }

    @Test
    void transformIfNecessary() {

      var fact = Fact.builder().ns("ns").type("type").id(factId).version(1).build("{\"y\":0}");

      assertThat(underTest.transformIfNecessary(fact, 2)).isEqualTo(fact);

      verifyNoInteractions(cache, trans);
    }
  }
}
