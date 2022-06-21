package org.factcast.store.internal.blacklist;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import org.factcast.store.internal.listen.PgListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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
