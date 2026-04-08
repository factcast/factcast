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
package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collection;
import org.factcast.core.TestHelper;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.*;

public class SubscriptionRequestTest {

  @Test
  void testCatchupNullSpec() {
    Assertions.assertThrows(
        NullPointerException.class, () -> SubscriptionRequest.catchup((FactSpec) null));
  }

  @Test
  void testFollowNullSpec() {
    Assertions.assertThrows(
        NullPointerException.class, () -> SubscriptionRequest.follow((FactSpec) null));
  }

  @Test
  void testFollowDelayNullSpec() {
    Assertions.assertThrows(NullPointerException.class, () -> SubscriptionRequest.follow(1, null));
  }

  @Test
  void testCatchup() {
    FactSpec s = FactSpec.ns("xx");
    final SubscriptionRequest r = SubscriptionRequest.catchup(s).fromScratch();
    assertTrue(r.specs().contains(s));
    assertEquals(1, r.specs().size());
  }

  @Test
  void testCatchupCollection() {
    FactSpec ns1 = FactSpec.ns("ns1");
    FactSpec ns2 = FactSpec.ns("ns2");
    final SubscriptionRequest r =
        SubscriptionRequest.catchup(Arrays.asList(ns1, ns2)).fromScratch();
    assertTrue(r.specs().contains(ns1));
    assertTrue(r.specs().contains(ns2));
    assertEquals(2, r.specs().size());
    assertFalse(r.continuous());
  }

  @Test
  void testFollowCollection() {
    FactSpec ns1 = FactSpec.ns("ns1");
    FactSpec ns2 = FactSpec.ns("ns2");
    final SubscriptionRequest r = SubscriptionRequest.follow(Arrays.asList(ns1, ns2)).fromScratch();
    assertTrue(r.specs().contains(ns1));
    assertTrue(r.specs().contains(ns2));
    assertEquals(2, r.specs().size());
    assertTrue(r.continuous());
  }

  @Test
  void testCatchupCollectionNullParameters() {
    TestHelper.expectNPE(() -> SubscriptionRequest.catchup((Collection<FactSpec>) null));
  }

  @Test
  void testFollowCollectionNullParameters() {
    TestHelper.expectNPE(() -> SubscriptionRequest.follow((Collection<FactSpec>) null));
  }

  @Test
  void testFollow() {
    FactSpec s = FactSpec.ns("xx");
    final SubscriptionRequest r = SubscriptionRequest.follow(s).fromScratch();
    assertTrue(r.specs().contains(s));
    assertEquals(1, r.specs().size());
  }

  @Test
  void testFollowMaxDelay() {
    FactSpec s = FactSpec.ns("xx");
    final SubscriptionRequest r = SubscriptionRequest.follow(17, s).fromScratch();
    assertTrue(r.specs().contains(s));
    assertEquals(1, r.specs().size());
    assertEquals(17, r.maxBatchDelayInMs());
  }

  @Test
  void testMinimumMaxDelay() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> {
              SubscriptionRequest r =
                  SubscriptionRequest.follow(1, FactSpec.ns("asd")).fromScratch();
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  // overloads with projection/aggregate class

  @Test
  void testFollowWithClass() {
    FactSpec s = FactSpec.ns("xx");
    final SubscriptionRequest r =
        SubscriptionRequest.follow(s).withDebugHintFrom(MyProjection.class).fromScratch();
    assertTrue(r.specs().contains(s));
    assertEquals(1, r.specs().size());
    assertTrue(r.continuous());
    assertThat(r.debugInfo()).contains("MyProjection");
  }

  @Test
  void testFollowCollectionWithClass() {
    FactSpec ns1 = FactSpec.ns("ns1");
    FactSpec ns2 = FactSpec.ns("ns2");
    final SubscriptionRequest r =
        SubscriptionRequest.follow(Arrays.asList(ns1, ns2))
            .withDebugHintFrom(MyProjection.class)
            .fromScratch();
    assertTrue(r.specs().contains(ns1));
    assertTrue(r.specs().contains(ns2));
    assertEquals(2, r.specs().size());
    assertTrue(r.continuous());
    assertThat(r.debugInfo()).contains("MyProjection");
  }

  @Test
  void testCatchupWithClass() {
    FactSpec s = FactSpec.ns("xx");
    final SubscriptionRequest r =
        SubscriptionRequest.catchup(s).withDebugHintFrom(MyProjection.class).fromScratch();
    assertTrue(r.specs().contains(s));
    assertEquals(1, r.specs().size());
    assertFalse(r.continuous());
    assertThat(r.debugInfo()).contains("MyProjection");
  }

  @Test
  void testCatchupCollectionWithClass() {
    FactSpec ns1 = FactSpec.ns("ns1");
    FactSpec ns2 = FactSpec.ns("ns2");
    final SubscriptionRequest r =
        SubscriptionRequest.builder()
            .withDebugHintFrom(MyProjection.class)
            .catchup(Arrays.asList(ns1, ns2))
            .fromScratch();
    assertTrue(r.specs().contains(ns1));
    assertTrue(r.specs().contains(ns2));
    assertEquals(2, r.specs().size());
    assertFalse(r.continuous());
    assertThat(r.debugInfo()).contains(MyProjection.class.getName());
  }

  @Test
  void testDebugInfoWithoutClass() {
    SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("xx")).fromScratch();
    assertThat(r.debugInfo()).doesNotContain("|");
  }

  @Test
  void testDebugInfoWithClass() {
    SubscriptionRequest r =
        SubscriptionRequest.builder()
            .catchup(FactSpec.ns("xx"))
            .withDebugHintFrom(MyProjection.class)
            .fromScratch();
    assertThat(r.debugInfo()).endsWith(MyProjection.class.getName());
  }

  static class MyProjection {}
}
