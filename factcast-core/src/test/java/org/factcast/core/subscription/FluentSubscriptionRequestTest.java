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

import java.util.LinkedList;
import java.util.UUID;
import lombok.val;
import org.factcast.core.spec.FactSpec;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FluentSubscriptionRequestTest {

  private static final boolean EPHEMERAL = true;
  private static final long MAX_BATCH_DELAY_IN_MS = 58;
  private static final long KEEPALIVE_INTERVAL_IN_MS = 89;
  private static final boolean CONTINUOUS = true;
  private static final UUID STARTING_AFTER = UUID.randomUUID();
  private static final String DEBUG_INFO = "DEBUG_INFO";
  private static final String PID = "PID";
  @Mock private FactSpec factSpec;
  @InjectMocks private FluentSubscriptionRequest underTest;

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
    assertTrue(debugInfo.contains(getClass().getSimpleName()));
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

  @Test
  void setMaxBatchDelayInMs() {
    SubscriptionRequest r =
        SubscriptionRequest.catchup(FactSpec.ns("foo")).withMaxBatchDelayInMs(21).fromScratch();
    assertEquals(r.maxBatchDelayInMs(), 21);
  }

  @Test
  void setKeepaliveIntervalInMs() {
    SubscriptionRequest r =
        SubscriptionRequest.catchup(FactSpec.ns("foo"))
            .withKeepaliveIntervalInMs(4444)
            .fromScratch();
    assertEquals(r.keepaliveIntervalInMs(), 4444);
  }

  @Test
  void setKeepaliveIntervalInMs_lowerBound() {
    assertThatThrownBy(
            () -> {
              SubscriptionRequest.catchup(FactSpec.ns("foo"))
                  .withKeepaliveIntervalInMs(4)
                  .fromScratch();
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setMaxBatchDelayInMs_lowerBound() {
    assertThatThrownBy(
            () -> {
              SubscriptionRequest.catchup(FactSpec.ns("foo"))
                  .withMaxBatchDelayInMs(1)
                  .fromScratch();
            })
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void setMaxBatchDelayInMs_lowerBoundOk() {
    SubscriptionRequest.catchup(FactSpec.ns("foo")).withMaxBatchDelayInMs(10).fromScratch();
  }
}
