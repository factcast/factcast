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
package org.factcast.store.internal.listen;

import com.google.common.eventbus.*;
import java.util.concurrent.atomic.*;
import javax.sql.DataSource;
import lombok.*;
import org.factcast.store.internal.notification.*;
import org.springframework.beans.factory.*;
import org.springframework.jdbc.core.JdbcTemplate;

@RequiredArgsConstructor
public class NudgeNotificationHandler implements SmartInitializingSingleton, DisposableBean {
  private final @NonNull EventBus bus;
  private final @NonNull DataSource ds;
  private final AtomicLong notificationSer = new AtomicLong(0);
  private JdbcTemplate jdbc;

  @Override
  public void destroy() throws Exception {
    bus.unregister(this);
  }

  @Override
  public void afterSingletonsInstantiated() {
    bus.register(this);
    this.jdbc = new JdbcTemplate(ds);
  }

  @Subscribe
  public void nudge(NudgeNotification nudgeNotification) {

    String baseExists = "SELECT (exists(select 1 from notification where ser=?))";

    if (notificationSer.get() == 0 || !jdbc.queryForObject(baseExists, Boolean.class)) {

      bus.post(FactInsertionNotification.internal());
    }

    fetchPairsAndDispatch();
    // TODO schedule subsequent polls

  }

  private void fetchPairsAndDispatch() {
    jdbc.query(
        "SELECT max(ser) as ser,ns,type FROM notification WHERE ser > ? GROUP BY DISTINCT(ns,type) ORDER BY ser",
        new Object[] {notificationSer.get()},
        rs -> {
          bus.post(FactInsertionNotification.internal(rs.getString(2), rs.getString(3)));
          notificationSer.set(rs.getLong(1));
        });
  }
}
