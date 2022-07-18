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
package org.factcast.core.subscription.transformation;

import java.util.*;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import com.google.common.collect.Lists;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FactTransformersTest {

  @Test
  void testTransformNotNecessaryEmpty() throws Exception {
    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    FactTransformers uut = new FactTransformers(requestedVersions);

    assertThat(uut.prepareTransformation(probe)).isNull();
  }

  @Test
  void testTransformNotNecessary_version0() throws Exception {

    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    String ns = probe.ns();
    String type = probe.type();
    requestedVersions.add(ns, type, 0);

    FactTransformers uut = new FactTransformers(requestedVersions);
    assertThat(uut.prepareTransformation(probe)).isNull();
  }

  @Test
  void testTransformNotNecessary_versionMatches() throws Exception {

    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    String ns = probe.ns();
    String type = probe.type();
    requestedVersions.add(ns, type, 33);

    FactTransformers uut = new FactTransformers(requestedVersions);

    assertThat(uut.prepareTransformation(probe)).isNull();
  }

  @Test
  void testTransformNecessary() throws Exception {

    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    String ns = probe.ns();
    String type = probe.type();
    requestedVersions.add(ns, type, 34);

    FactTransformers uut = new FactTransformers(requestedVersions);

    TransformationRequest actual = uut.prepareTransformation(probe);
    assertThat(actual).isNotNull();
    assertThat(actual.targetVersions()).isEqualTo(Collections.singleton(34));
    assertThat(actual.toTransform()).isSameAs(probe);
  }

  @Test
  void noTransformationRequired() throws Exception {

    RequestedVersions requestedVersions = new RequestedVersions();
    Fact probe = new TestFact().version(33);
    String ns = probe.ns();
    String type = probe.type();
    requestedVersions.add(ns, "someOtherType", 34);

    FactTransformers uut = new FactTransformers(requestedVersions);

    assertThat(uut.prepareTransformation(probe)).isNull();
  }

  @Test
  void fromRequest() throws Exception {
    SubscriptionRequest req = mock(SubscriptionRequest.class);
    FactSpec spec1 = FactSpec.ns("ns1").type("type1").version(11);
    FactSpec spec2 = FactSpec.ns("ns2").type("type2").version(12);
    FactSpec spec3 = FactSpec.ns("ns3").type("type3").version(13);
    List<FactSpec> specs = Lists.newArrayList(spec1, spec2, spec3);
    when(req.specs()).thenReturn(specs);

    RequestedVersions requested = FactTransformers.createFor(req).requested();
    assertThat(requested.get("ns1", "type1")).hasSize(1).containsExactly(11);
    assertThat(requested.get("ns2", "type2")).hasSize(1).containsExactly(12);
    assertThat(requested.get("ns3", "type3")).hasSize(1).containsExactly(13);
    assertThat(requested.get("ns1", "type2")).isEmpty();
    assertThat(requested.get("xyz", "type")).isEmpty();
  }
}
