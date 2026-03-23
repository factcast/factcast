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
import org.factcast.core.spec.FactSpec;
import org.factcast.core.util.StackTraceCallerHelper;
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
    // format: UUID (ClassName.methodName:lineNumber)
    assertThat(debugInfo).matches("^[0-9a-f-]+ \\(.+\\..+:\\d+\\)$");
  }

  @Test
  void findCallerFrame_directApiUsage() {
    StackTraceElement[] stack = {
      elem("org.factcast.core.subscription.FluentSubscriptionRequest", "createDebugInfo"),
      elem("org.factcast.core.subscription.FluentSubscriptionRequest", "<init>"),
      elem("org.factcast.core.subscription.SubscriptionRequest", "catchup"),
      elem("com.myapp.service.OrderService", "placeOrder"),
    };

    StackTraceElement result = StackTraceCallerHelper.findCallerFrame(stack);
    assertThat(result.getClassName()).isEqualTo("com.myapp.service.OrderService");
    assertThat(result.getMethodName()).isEqualTo("placeOrder");
  }

  @Test
  void findCallerFrame_factusUsage() {
    StackTraceElement[] stack = {
      elem("org.factcast.core.subscription.FluentSubscriptionRequest", "createDebugInfo"),
      elem("org.factcast.core.subscription.FluentSubscriptionRequest", "<init>"),
      elem("org.factcast.core.subscription.SubscriptionRequest", "catchup"),
      elem("org.factcast.factus.FactusImpl", "catchupProjection"),
      elem("org.factcast.factus.FactusImpl", "fetch"),
      elem("com.myapp.service.OrderService", "updateProjection"),
    };

    StackTraceElement result = StackTraceCallerHelper.findCallerFrame(stack);
    assertThat(result.getClassName()).isEqualTo("com.myapp.service.OrderService");
    assertThat(result.getMethodName()).isEqualTo("updateProjection");
  }

  @Test
  void findCallerFrame_skipsSpringProxies() {
    StackTraceElement[] stack = {
      elem("org.factcast.core.subscription.FluentSubscriptionRequest", "createDebugInfo"),
      elem("org.factcast.core.subscription.SubscriptionRequest", "catchup"),
      elem("org.factcast.factus.FactusImpl", "fetch"),
      elem("org.springframework.cglib.proxy.MethodProxy", "invoke"),
      elem("org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation", "proceed"),
      elem("com.myapp.service.OrderService", "fetchOrder"),
    };

    StackTraceElement result = StackTraceCallerHelper.findCallerFrame(stack);
    assertThat(result.getClassName()).isEqualTo("com.myapp.service.OrderService");
    assertThat(result.getMethodName()).isEqualTo("fetchOrder");
  }

  @Test
  void findCallerFrame_allInternalFallsBackToIndex3() {
    StackTraceElement[] stack = {
      elem("org.factcast.core.A", "m1"),
      elem("org.factcast.core.B", "m2"),
      elem("org.factcast.core.C", "m3"),
      elem("org.factcast.core.D", "m4"),
    };

    StackTraceElement result = StackTraceCallerHelper.findCallerFrame(stack);
    assertThat(result.getClassName()).isEqualTo("org.factcast.core.D");
  }

  private static StackTraceElement elem(String className, String methodName) {
    return new StackTraceElement(
        className, methodName, className.substring(className.lastIndexOf('.') + 1) + ".java", 42);
  }

  @Test
  void failsCatchupIfFactSpecListIsEmpty() {
    LinkedList<FactSpec> l = new LinkedList<FactSpec>();
    assertThrows(
        IllegalArgumentException.class,
        () -> SubscriptionRequest.catchup(l).fromScratch().debugInfo());
  }

  @Test
  void failsFollowIfFactSpecListIsEmpty() {
    LinkedList<FactSpec> l = new LinkedList<FactSpec>();
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
