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
package org.factcast.store.pgsql.registry.transformation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.subscription.FactTransformerService;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.pgsql.internal.RequestedVersions;
import org.factcast.store.pgsql.registry.NOPRegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetricsOperation;
import org.factcast.store.pgsql.registry.metrics.SupplierWithException;
import org.factcast.store.pgsql.registry.transformation.cache.TransformationCache;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChain;
import org.factcast.store.pgsql.registry.transformation.chains.TransformationChains;
import org.factcast.store.pgsql.registry.transformation.chains.Transformer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FactTransformersImplTest {

  @Mock TransformationChains chains;

  @Mock Transformer trans;

  @Mock TransformationCache cache;

  @Mock TransformationChain chain;

  @Spy final RegistryMetrics registryMetrics = new NOPRegistryMetrics();

  @Test
  public void testTransformNotNecessaryEmpty() throws Exception {

    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    FactTransformerService service =
        new FactTransformerServiceImpl(chains, trans, cache, registryMetrics);
    FactTransformersImpl uut =
        new FactTransformersImpl(requestedVersions, service, registryMetrics);

    assertThat(uut.transformIfNecessary(probe)).isSameAs(probe);

    verifyNoInteractions(registryMetrics);
  }

  @Test
  public void testTransformNotNecessary_version0() throws Exception {

    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    String ns = probe.ns();
    String type = probe.type();
    requestedVersions.add(ns, type, 0);
    FactTransformerService service =
        new FactTransformerServiceImpl(chains, trans, cache, registryMetrics);

    FactTransformersImpl uut =
        new FactTransformersImpl(requestedVersions, service, registryMetrics);
    assertThat(uut.transformIfNecessary(probe)).isSameAs(probe);

    verifyNoInteractions(registryMetrics);
  }

  @Test
  public void testTransformNotNecessary_versionMatches() throws Exception {

    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    String ns = probe.ns();
    String type = probe.type();
    requestedVersions.add(ns, type, 33);

    FactTransformerService service =
        new FactTransformerServiceImpl(chains, trans, cache, registryMetrics);

    FactTransformersImpl uut =
        new FactTransformersImpl(requestedVersions, service, registryMetrics);

    assertThat(uut.transformIfNecessary(probe)).isSameAs(probe);

    verifyNoInteractions(registryMetrics);
  }

  @Test
  public void testTransform() throws Exception {
    String chainId = "chainId";
    Fact probe = new TestFact().version(1);
    String ns = probe.ns();
    String type = probe.type();
    RequestedVersions requestedVersions = new RequestedVersions();
    requestedVersions.add(ns, type, 33);

    when(chains.get(eq(TransformationKey.from(probe)), eq(probe.version()), eq(33)))
        .thenReturn(chain);
    when(chain.id()).thenReturn(chainId);
    Map<String, Object> propertyMap = new HashMap<>();
    JsonNode transformedJsonNode = FactCastJson.toJsonNode(propertyMap);
    when(trans.transform(any(), eq(FactCastJson.readTree(probe.jsonPayload()))))
        .thenReturn(transformedJsonNode);

    FactTransformerService service =
        new FactTransformerServiceImpl(chains, trans, cache, registryMetrics);

    FactTransformersImpl uut =
        new FactTransformersImpl(requestedVersions, service, registryMetrics);

    Fact transformed = uut.transformIfNecessary(probe);
    assertThat(transformed.jsonPayload()).isEqualTo(transformedJsonNode.toString());

    verify(cache).find(eq(probe.id()), eq(33), eq(chainId));

    verify(registryMetrics)
        .timed(
            eq(RegistryMetricsOperation.TRANSFORMATION), any(), any(SupplierWithException.class));
  }
}
