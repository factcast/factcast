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

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedList;
import lombok.val;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.*;

public class FluentSubscriptionRequestTest {

  @Test
  void testFromSubscription() {
    SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromNowOn();
    assertTrue(r.ephemeral());
  }

  @Test
  void testFromNull() {
    Assertions.assertThrows(
        NullPointerException.class,
        () -> SubscriptionRequest.catchup(FactSpec.ns("foo")).from(null));
  }

  @Test
  void testFollowNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> SubscriptionRequest.follow((FactSpec) null));
  }

  @Test
  void testFollowNullable() {
    SubscriptionRequest req = SubscriptionRequest.follow(FactSpec.ns("foo")).fromNullable(null);
    assertFalse(req.startingAfter().isPresent());
  }

  @Test
  void testCatchupNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> SubscriptionRequest.catchup((FactSpec) null));
  }

  @Test
  void testOrNull() {
    Assertions.assertThrows(
        NullPointerException.class, () -> SubscriptionRequest.catchup(FactSpec.ns("foo")).or(null));
  }

  @Test
  void testToString() {
    SubscriptionRequest r = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch();
    assertSame(r.debugInfo(), r.toString());
  }

  @Test
  void testDebugInfo() {
    String debugInfo = SubscriptionRequest.catchup(FactSpec.ns("foo")).fromScratch().debugInfo();
    assertNotNull(debugInfo);
    assertTrue(debugInfo.contains(this.getClass().getSimpleName()));
    // method name
    assertTrue(debugInfo.contains("testDebugInfo"));
  }

  @Test
  void failsCatchupIfFactSpecListIsEmpty() {
    val l = new LinkedList<FactSpec>();
    assertThrows(
        IllegalArgumentException.class,
        () -> SubscriptionRequest.catchup(l).fromScratch().debugInfo());
  }

  @Test
  void failsFollowIfFactSpecListIsEmpty() {
    val l = new LinkedList<FactSpec>();
    assertThrows(
        IllegalArgumentException.class,
        () -> SubscriptionRequest.follow(l).fromScratch().debugInfo());
  }
}
