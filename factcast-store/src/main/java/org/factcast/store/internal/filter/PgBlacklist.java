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
package org.factcast.store.internal.filter;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.listen.PgListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
public class PgBlacklist implements SmartInitializingSingleton, DisposableBean {
  private final EventBus bus;
  private final Fetcher fetcher;
  private final Set<UUID> blocked = new CopyOnWriteArraySet<>();

  private void read() {
    Set<UUID> currentList = fetcher.get();
    // we should not just replace it in order to not mess with the reference
    Sets.SetView<UUID> toRemove = Sets.difference(blocked, currentList);
    blocked.removeAll(toRemove);
    blocked.addAll(currentList);
  }

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
    read();
  }

  public boolean isBlocked(@NonNull UUID factId) {
    return blocked.contains(factId);
  }

  @Subscribe
  public void on(PgListener.BlacklistChangeSignal signal) {
    log.info("A change on blacklist table was triggered.");
    read();
  }

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
  }

  @RequiredArgsConstructor
  @VisibleForTesting
  public static class Fetcher implements Supplier<Set<UUID>> {
    final JdbcTemplate jdbc;

    @Override
    public Set<UUID> get() {
      log.warn("Fetching blacklist");
      return Sets.newHashSet(jdbc.queryForList("SELECT id FROM blacklist", UUID.class));
    }
  }
}
