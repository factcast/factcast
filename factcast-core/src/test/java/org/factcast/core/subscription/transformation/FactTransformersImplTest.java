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

import static org.assertj.core.api.Assertions.assertThat;

import org.factcast.core.Fact;
import org.factcast.core.TestFact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactTransformersImplTest {

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
    assertThat(actual.targetVersion()).isEqualTo(34);
    assertThat(actual.toTransform()).isSameAs(probe);
  }
}
