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
package org.factcast.store.registry.transformation.chains;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import java.util.ArrayList;
import org.factcast.core.subscription.MissingTransformationInformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.script.engine.EngineFactory;
import org.factcast.script.engine.graaljs.GraalJSEngineCache;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.SingleTransformation;
import org.factcast.store.registry.transformation.Transformation;
import org.factcast.store.registry.transformation.TransformationKey;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

public class TransformationChainsTest {
  final SchemaRegistry r = mock(SchemaRegistry.class);

  final RegistryMetrics registryMetrics = Mockito.spy(new NOPRegistryMetrics());

  final TransformationChains uut = new TransformationChains(r, registryMetrics);

  final TransformationKey key = TransformationKey.of("ns", "UserCreated");

  final EngineFactory engineFactory = new GraalJSEngineCache();
  final GraalJsTransformer transformer = new GraalJsTransformer(engineFactory);

  @Test
  void testAddingNewArray() throws Exception {
    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 1, 2, "function transform(ev) {ev.arr = [1,2,3,'4']}"));
    all.add(SingleTransformation.of(key, 2, 3, "function transform(ev) {ev.newField=true}"));

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 1, 3);

    assertEquals(1, chain.fromVersion());
    assertEquals(3, chain.toVersion());
    assertEquals(key, chain.key());
    assertEquals("[1, 2, 3]", chain.id());
    assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString()).isEqualTo("{\"arr\":[1,2,3,\"4\"],\"newField\":true}");
  }

  @Test
  void testStraightLine() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 1, 2, js(1)));
    all.add(SingleTransformation.of(key, 2, 3, js(2)));
    all.add(SingleTransformation.of(key, 3, 4, js(3)));
    all.add(SingleTransformation.of(key, 4, 5, js(4)));
    all.add(SingleTransformation.of(key, 5, 6, js(5)));
    all.add(SingleTransformation.of(key, 6, 7, js(6)));

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 2, 5);

    assertEquals(2, chain.fromVersion());
    assertEquals(5, chain.toVersion());
    assertEquals(key, chain.key());
    assertEquals("[2, 3, 4, 5]", chain.id());
    assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString()).isEqualTo("{\"stage2\":true,\"stage3\":true,\"stage4\":true}");
  }

  @Test
  void testUnreachable() {

    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 1, 2, js(1)));
    all.add(SingleTransformation.of(key, 2, 3, js(2)));
    all.add(SingleTransformation.of(key, 5, 6, js(5)));
    all.add(SingleTransformation.of(key, 6, 7, js(6)));

    when(r.get(key)).thenReturn(all);

    assertThrows(MissingTransformationInformationException.class, () -> uut.get(key, 1, 7));
    verify(registryMetrics)
        .count(
            eq(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO),
            eq(
                Tags.of(
                    Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
                    Tag.of("from", "1"),
                    Tag.of("to", "7"))));
  }

  @Test
  void testShortcut() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 1, 2, js(1)));
    all.add(SingleTransformation.of(key, 2, 3, js(2)));
    all.add(SingleTransformation.of(key, 5, 6, js(5)));
    all.add(SingleTransformation.of(key, 6, 7, js(6)));

    all.add(SingleTransformation.of(key, 2, 6, js(6)));

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 1, 7);

    assertEquals(1, chain.fromVersion());
    assertEquals(7, chain.toVersion());
    assertEquals(key, chain.key());
    assertEquals("[1, 2, 6, 7]", chain.id());
    assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString()).isEqualTo("{\"stage1\":true,\"stage6\":true}");
  }

  @Test
  void testConcurringShortcuts() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 1, 2, js(1)));
    all.add(SingleTransformation.of(key, 2, 3, js(2)));
    all.add(SingleTransformation.of(key, 3, 4, js(3)));
    all.add(SingleTransformation.of(key, 4, 5, js(4)));
    all.add(SingleTransformation.of(key, 5, 6, js(5)));
    all.add(SingleTransformation.of(key, 6, 7, js(6)));

    all.add(SingleTransformation.of(key, 2, 5, js(2)));
    all.add(SingleTransformation.of(key, 1, 4, js(1))); // <- should win

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 1, 7);

    assertEquals(1, chain.fromVersion());
    assertEquals(7, chain.toVersion());
    assertEquals(key, chain.key());
    assertEquals("[1, 2, 5, 6, 7]", chain.id());
    assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString())
        .isEqualTo("{\"stage1\":true,\"stage2\":true,\"stage5\":true,\"stage6\":true}");
  }

  @Test
  void testTargetNotFound() {
    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 1, 2, js(1)));
    all.add(SingleTransformation.of(key, 2, 3, js(2)));
    all.add(SingleTransformation.of(key, 3, 4, js(3)));
    all.add(SingleTransformation.of(key, 4, 5, js(4)));
    all.add(SingleTransformation.of(key, 5, 6, js(5)));
    all.add(SingleTransformation.of(key, 6, 7, js(6)));
    all.add(SingleTransformation.of(key, 3, 5, js(100)));

    when(r.get(key)).thenReturn(all);

    assertThrows(MissingTransformationInformationException.class, () -> uut.get(key, 2, 99));
    verify(registryMetrics)
        .count(
            eq(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO),
            eq(
                Tags.of(
                    Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
                    Tag.of("from", "2"),
                    Tag.of("to", "99"))));
  }

  @Test
  void testSyntheticTransformation() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 2, 1, js(1)));
    all.add(SingleTransformation.of(key, 3, 2, null));

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 3, 1);

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString()).isEqualTo("{\"stage1\":true}");
  }

  @Test
  void testNoTransformationForKey() {
    ArrayList<Transformation> all = Lists.newArrayList();
    when(r.get(key)).thenReturn(all);

    assertThrows(MissingTransformationInformationException.class, () -> uut.get(key, 2, 99));
    verify(registryMetrics)
        .count(
            eq(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO),
            eq(
                Tags.of(
                    Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
                    Tag.of("from", "2"),
                    Tag.of("to", "99"))));
  }

  private String js(int n) {
    return "function transform(event){ event.stage" + n + " = true }";
  }
}
