/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.registry;

import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import lombok.SneakyThrows;
import org.factcast.store.internal.notification.SchemaStoreChangeNotification;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgSchemaStoreChangeListenerTest {

  @Spy private EventBus bus = new EventBus();

  @Mock private SchemaRegistry registry;

  @InjectMocks private PgSchemaStoreChangeListener underTest;

  @Nested
  class WhenAfteringSingletonsInstantiated {
    @Test
    void registersOnBus() {
      underTest.afterSingletonsInstantiated();
      verify(bus, times(1)).register(underTest);
    }
  }

  @Nested
  class WhenDisposing {
    @SneakyThrows
    @Test
    void unregisters() {
      underTest.afterSingletonsInstantiated();
      underTest.destroy();
      verify(bus).unregister(underTest);
    }
  }

  @Nested
  class WhenOning {
    private SchemaStoreChangeNotification signal;
    private final SchemaKey key = SchemaKey.of("ns", "type", 1);

    @Test
    void invalidatesCache() {
      signal = new SchemaStoreChangeNotification(key.ns(), key.type(), key.version(), 1L);
      underTest.on(signal);
      verify(registry, times(1)).invalidateNearCache(key);
    }
  }
}
