package org.factcast.store.registry.transformation.cache;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.listen.PgListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class PgTransformationStoreChangeListener implements SmartInitializingSingleton, DisposableBean {

  private final EventBus bus;

  private final PgTransformationCache cache;

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
  }

  @Subscribe
  public void on(PgListener.TransformationStoreDeleteSignal signal) {
    cache.invalidateTransformationFor(signal.ns(), signal.type());
  }
                 @Override
  public void destroy() throws Exception {
    bus.unregister(this);
  }
}
