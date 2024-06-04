/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.store.internal.telemetry;

import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.jupiter.api.Test;

class PgStoreTelemetryTest {

  @Test
  void setsEventBusOnConstruction() {
    var eventBus = mock(EventBus.class);
    var listener = new Object();

    var uut = new PgStoreTelemetry(eventBus);
    uut.register(listener);

    verify(eventBus).register(listener);
  }

  @Test
  void delegatesPostToEventBus() {
    var eventBus = mock(EventBus.class);
    var req = new SubscriptionRequestTO();
    var uut = new PgStoreTelemetry(eventBus);

    uut.onConnect(req);

    verify(eventBus).post(new PgStoreTelemetry.Connect(req));
  }
}
