package org.factcast.store.registry;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.listen.PgListener;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class PgSchemaStoreChangeListener implements SmartInitializingSingleton, DisposableBean {

  private final EventBus bus;

  private final SchemaRegistry registry;

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
  }

  @Subscribe
  public void on(PgListener.SchemaStoreChangeSignal signal) {
    registry.invalidateNearCache(SchemaKey.of(signal.ns(), signal.type(), signal.version()));
  }

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
  }
}
