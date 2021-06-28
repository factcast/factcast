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
package org.factcast.store.pgsql.internal;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.pgsql.internal.PgFactStream.RatioLogLevel;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PgFactStreamTest {

  @Mock SubscriptionRequest req;
  @Mock SubscriptionImpl sub;
  @Mock PgSynchronizedQuery query;
  @Mock FastForwardTarget ffwdTarget;
  @Mock PgMetrics metrics;
  @InjectMocks PgFactStream uut;

  @Test
  public void testConnectNullParameter() {
    assertThrows(NullPointerException.class, () -> uut.connect(null));
  }

  @Test
  void logsCatchupTransformationStats() {
    uut = spy(uut);
    doNothing().when(uut).catchup(any());
    doNothing().when(uut).logCatchupTransformationStats();

    uut.catchupAndFollow(req, sub, query);

    verify(uut).logCatchupTransformationStats();
  }

  @Test
  void debugLevelIfToFewFacts() {
    assertThat(uut.calculateLogLevel(5, 100)).isSameAs(RatioLogLevel.DEBUG);
    assertThat(uut.calculateLogLevel(5, 0)).isSameAs(RatioLogLevel.DEBUG);
    assertThat(uut.calculateLogLevel(32, 80)).isSameAs(RatioLogLevel.DEBUG);
  }

  @Test
  void debugLevelIfLowRatio() {
    assertThat(uut.calculateLogLevel(1000, 5)).isSameAs(RatioLogLevel.DEBUG);
  }

  @Test
  void infoLevelIfRatioSignificant() {
    assertThat(uut.calculateLogLevel(1000, 10)).isSameAs(RatioLogLevel.INFO);
  }

  @Test
  void warnLevelIfRatioTooHigh() {
    assertThat(uut.calculateLogLevel(1000, 20)).isSameAs(RatioLogLevel.WARN);
  }
}
