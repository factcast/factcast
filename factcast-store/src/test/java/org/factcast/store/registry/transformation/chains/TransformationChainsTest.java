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

import java.util.*;

import org.factcast.core.subscription.MissingTransformationInformationException;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.script.JSEngineFactory;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.SingleTransformation;
import org.factcast.store.registry.transformation.Transformation;
import org.factcast.store.registry.transformation.TransformationKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TransformationChainsTest {
  final SchemaRegistry r = mock(SchemaRegistry.class);

  final RegistryMetrics registryMetrics = Mockito.spy(new NOPRegistryMetrics());

  final TransformationChains uut = new TransformationChains(r, registryMetrics);

  final TransformationKey key = TransformationKey.of("ns", "UserCreated");
  // TODO
  final JSEngineFactory engineFactory = new GraalJSEngineFactory();
  final JsTransformer transformer = new JsTransformer(engineFactory);

  @Test
  void testAddingNewArray() throws Exception {
    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 1, 2, "function transform(ev) {ev.arr = [1,2,3,'4']}"));
    all.add(SingleTransformation.of(key, 2, 3, "function transform(ev) {ev.newField=true}"));

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 1, Collections.singleton(3));

    Assertions.assertEquals(1, chain.fromVersion());
    Assertions.assertEquals(3, chain.toVersion());
    Assertions.assertEquals(key, chain.key());
    Assertions.assertEquals("[1, 2, 3]", chain.id());
    org.assertj.core.api.Assertions.assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString()).isEqualTo("{\"arr\":[1,2,3,\"4\"],\"newField\":true}");
  }

  @Test
  void testStraightLine() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 2, Collections.singleton(5));

    Assertions.assertEquals(2, chain.fromVersion());
    Assertions.assertEquals(5, chain.toVersion());
    Assertions.assertEquals(key, chain.key());
    Assertions.assertEquals("[2, 3, 4, 5]", chain.id());
    org.assertj.core.api.Assertions.assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString()).isEqualTo("{\"stage2\":true,\"stage3\":true,\"stage4\":true}");
  }

  @Test
  void testUnreachable() {

    ArrayList<Transformation> all = Lists.newArrayList();

    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    unidir(all, key, 5, 6);
    unidir(all, key, 6, 7);

    when(r.get(key)).thenReturn(all);

    assertThrows(
        MissingTransformationInformationException.class,
        () -> uut.get(key, 1, Collections.singleton(7)));
    verify(registryMetrics)
        .count(
            eq(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO),
            ArgumentMatchers.eq(
                Tags.of(
                    Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
                    Tag.of("from", "1"),
                    Tag.of("to", "[7]"))));
  }

  @Test
  void testSingleShortcut() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);
    bidir(all, key, 5, 6);
    bidir(all, key, 6, 7);

    all.add(SingleTransformation.of(key, 2, 6, js(6)));

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 1, Collections.singleton(7));

    Assertions.assertEquals(1, chain.fromVersion());
    Assertions.assertEquals(7, chain.toVersion());
    Assertions.assertEquals(key, chain.key());
    Assertions.assertEquals("[1, 2, 6, 7]", chain.id());
    org.assertj.core.api.Assertions.assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString()).isEqualTo("{\"stage1\":true,\"stage6\":true}");
  }

  @Test
  void testConcurringShortcuts() throws Exception {
    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);
    bidir(all, key, 5, 6);
    bidir(all, key, 6, 7);

    unidir(all, key, 2, 5); // <- should win
    unidir(all, key, 1, 4);

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 1, Collections.singleton(7));

    Assertions.assertEquals(1, chain.fromVersion());
    Assertions.assertEquals(7, chain.toVersion());
    Assertions.assertEquals(key, chain.key());
    Assertions.assertEquals("[1, 2, 5, 6, 7]", chain.id());
    org.assertj.core.api.Assertions.assertThat(chain.transformationCode()).isPresent();

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual.toString())
        .isEqualTo("{\"stage1\":true,\"stage2\":true,\"stage5\":true,\"stage6\":true}");
  }

  @Test
  void testbiasToHigherVersionWhenPickingShortcut() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();

    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);

    unidir(all, key, 2, 4);
    unidir(all, key, 3, 5); // <- should win

    when(r.get(key)).thenReturn(all);
    TransformationChain chain = uut.get(key, 1, Collections.singleton(5));
    Assertions.assertEquals("[1, 2, 3, 5]", chain.id());
  }

  @Test
  void testShortestPath() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);

    when(r.get(key)).thenReturn(all);
    TransformationChain chain = uut.get(key, 1, Sets.newHashSet(4, 5));
    Assertions.assertEquals("[1, 2, 3, 4]", chain.id());
  }

  @Test
  void testShortestPath_preferHigherVersion() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);
    unidir(all, key, 3, 5);

    when(r.get(key)).thenReturn(all);
    TransformationChain chain = uut.get(key, 1, Sets.newHashSet(4, 5));
    Assertions.assertEquals("[1, 2, 3, 5]", chain.id());
  }

  @Test
  void testPreferShorterPathToLowerVersion() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);

    unidir(all, key, 1, 4);
    unidir(all, key, 3, 5); // should be chosen

    when(r.get(key)).thenReturn(all);
    TransformationChain chain = uut.get(key, 1, Collections.singleton(5));
    Assertions.assertEquals("[1, 4, 5]", chain.id());
  }

  @Test
  void testBiasToHigherTargetIfPathCostEqual() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);

    when(r.get(key)).thenReturn(all);
    TransformationChain chain = uut.get(key, 2, Sets.newHashSet(1, 3));
    Assertions.assertEquals("[2, 3]", chain.id());
  }

  @Test
  void testChooseNearerTargetEvenIfDownward() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);

    when(r.get(key)).thenReturn(all);
    TransformationChain chain = uut.get(key, 2, Sets.newHashSet(1, 4));
    Assertions.assertEquals("[2, 1]", chain.id());
  }

  @Test
  void testTargetNotFound() {
    ArrayList<Transformation> all = Lists.newArrayList();
    bidir(all, key, 1, 2);
    bidir(all, key, 2, 3);
    bidir(all, key, 3, 4);
    bidir(all, key, 4, 5);

    when(r.get(key)).thenReturn(all);

    assertThrows(
        MissingTransformationInformationException.class,
        () -> uut.get(key, 2, Collections.singleton(99)));
    verify(registryMetrics)
        .count(
            eq(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO),
            ArgumentMatchers.eq(
                Tags.of(
                    Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
                    Tag.of("from", "2"),
                    Tag.of("to", "[99]"))));
  }

  @Test
  void testSyntheticTransformation() throws Exception {

    ArrayList<Transformation> all = Lists.newArrayList();
    all.add(SingleTransformation.of(key, 2, 1, js(1)));
    all.add(SingleTransformation.of(key, 3, 2, null));

    when(r.get(key)).thenReturn(all);

    TransformationChain chain = uut.get(key, 3, Collections.singleton(1));

    JsonNode input = FactCastJson.readTree("{}");
    JsonNode actual = transformer.transform(chain, input);
    assertThat(actual).hasToString("{\"stage1\":true}");
  }

  @Test
  void testNoTransformationForKey() {
    ArrayList<Transformation> all = Lists.newArrayList();
    when(r.get(key)).thenReturn(all);

    assertThrows(
        MissingTransformationInformationException.class,
        () -> uut.get(key, 2, Collections.singleton(99)));
    verify(registryMetrics)
        .count(
            eq(RegistryMetrics.EVENT.MISSING_TRANSFORMATION_INFO),
            ArgumentMatchers.eq(
                Tags.of(
                    Tag.of(RegistryMetrics.TAG_IDENTITY_KEY, key.toString()),
                    Tag.of("from", "2"),
                    Tag.of("to", "[99]"))));
  }

  private void bidir(ArrayList<Transformation> all, TransformationKey key, int i, int j) {
    all.add(SingleTransformation.of(key, i, j, js(i)));
    all.add(SingleTransformation.of(key, j, i, js(j)));
  }

  private void unidir(ArrayList<Transformation> all, TransformationKey key, int i, int j) {
    all.add(SingleTransformation.of(key, i, j, js(i)));
  }

  private String js(int n) {
    return "function transform(event){ event.stage" + n + " = true }";
  }
}
