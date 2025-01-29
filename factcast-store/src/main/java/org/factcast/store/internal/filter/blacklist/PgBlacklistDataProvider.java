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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.common.eventbus.*;
import java.util.*;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.store.internal.notification.BlacklistChangeNotification;
import org.springframework.beans.factory.*;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
public final class PgBlacklistDataProvider
    implements SmartInitializingSingleton, DisposableBean, BlacklistDataProvider {
  private final EventBus bus;
  private final JdbcTemplate jdbc;
  private final Blacklist blacklist;

  public PgBlacklistDataProvider(
      @NonNull EventBus eventBus,
      @NonNull JdbcTemplate jdbcTemplate,
      @NonNull Blacklist blacklist) {
    this.bus = eventBus;
    this.jdbc = jdbcTemplate;
    this.blacklist = blacklist;
  }

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
    updateBlacklist(); // initially necessary
  }

  @Subscribe
  public void on(BlacklistChangeNotification signal) {
    log.debug("A potential change on blacklist table was triggered.");
    updateBlacklist();
  }

  @VisibleForTesting
  void updateBlacklist() {
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
