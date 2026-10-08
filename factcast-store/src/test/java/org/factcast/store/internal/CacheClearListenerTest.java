/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.store.internal;

import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import org.factcast.store.internal.notification.CacheClearNotification;
import org.factcast.store.registry.SchemaRegistry;
import org.factcast.store.registry.transformation.chains.TransformationChains;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CacheClearListenerTest {

  @Mock private EventBus bus;
  @Mock private TransformationChains chains;
  @Mock private SchemaRegistry registry;
  @InjectMocks private CacheClearListener underTest;

  @Nested
  class WhenAfteringSingletonsInstantiated {
    @Test
    void registersOnBus() {
      underTest.afterSingletonsInstantiated();
      verify(bus, times(1)).register(underTest);
    }
  }

  @Nested
  class OnCacheClearAllNotification {
    @Mock private CacheClearNotification ignore;

    @Test
    void clearsCaches() {
      underTest.on(ignore);
      verify(chains, times(1)).clearCache();
      verify(registry, times(1)).clearNearCache();
    }
  }

  @Nested
  class WhenDestroying {
    @Test
    void unregistersFromBus() throws Exception {
      underTest.afterSingletonsInstantiated();
      underTest.destroy();
      verify(bus).unregister(underTest);
    }
  }
}
