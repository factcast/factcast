package org.factcast.store.registry;

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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PgSchemaStoreChangeListenerTest {

  @Spy
  private EventBus bus = new EventBus();

  @Mock
  private SchemaRegistry registry;

  @InjectMocks
  private PgSchemaStoreChangeListener underTest;

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
    @Mock private PgListener.SchemaStoreChangeSignal signal;
    private SchemaKey key = SchemaKey.of("ns", "type", 1);

    @Test
    void invalidatesCache() {
      when(signal.ns()).thenReturn(key.ns());
      when(signal.type()).thenReturn(key.type());
      when(signal.version()).thenReturn(key.version());
      underTest.on(signal);
      verify(registry, times(1)).invalidateNearCache(key);
    }
  }
}