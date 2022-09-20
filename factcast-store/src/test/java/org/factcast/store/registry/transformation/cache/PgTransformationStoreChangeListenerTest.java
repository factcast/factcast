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
package org.factcast.store.registry.transformation.cache;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

import com.google.common.eventbus.EventBus;
import lombok.SneakyThrows;
import org.factcast.store.internal.listen.PgListener;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PgTransformationStoreChangeListenerTest {

  @Spy private EventBus bus = new EventBus();

  @Mock private TransformationCache transformationCache;

  @InjectMocks PgTransformationStoreChangeListener underTest;

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
    @Mock private PgListener.TransformationStoreChangeSignal signal;

    private SchemaKey key = SchemaKey.of("ns", "type", 1);

    @Test
    void invalidatesCache() {
      when(signal.ns()).thenReturn(key.ns());
      when(signal.type()).thenReturn(key.type());
      underTest.on(signal);
      verify(transformationCache, times(1)).invalidateTransformationFor(key.ns(), key.type());
    }
  }
}
