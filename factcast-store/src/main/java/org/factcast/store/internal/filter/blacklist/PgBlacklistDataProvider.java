/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store.internal.filter.blacklist;

import com.google.common.collect.Sets;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.util.Set;
import java.util.UUID;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.listen.PgListener;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public final class PgBlacklistDataProvider
    implements SmartInitializingSingleton, DisposableBean, BlacklistDataProvider {
  private final EventBus bus;
  private final JdbcTemplate jdbc;
  private final Blacklist blacklist;

  public PgBlacklistDataProvider(
          @NonNull EventBus eventBus, @NonNull JdbcTemplate jdbcTemplate, @NonNull Blacklist blacklist) {
    this.bus = eventBus;
    this.jdbc = jdbcTemplate;
    this.blacklist = blacklist;
  }

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
    updateBlacklist();
  }

  @Subscribe
  public void on(PgListener.BlacklistChangeSignal signal) {
    log.info("A change on blacklist table was triggered.");
    updateBlacklist();
  }

  private void updateBlacklist() {
    blacklist.accept(fetchBlacklist());
  }

  private Set<UUID> fetchBlacklist() {
    return Sets.newHashSet(jdbc.queryForList("SELECT id FROM blacklist", UUID.class));
  }

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
  }
}
