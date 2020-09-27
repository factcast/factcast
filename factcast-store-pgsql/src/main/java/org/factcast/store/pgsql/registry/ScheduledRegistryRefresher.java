/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.pgsql.registry;

import com.google.common.base.Stopwatch;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;

@Value
@Slf4j
public class ScheduledRegistryRefresher {

  SchemaRegistry registry;

  @Scheduled(cron = "${factcast.store.pgsql.schemaStoreRefreshCron:*/60 * * * * *}")
  @SchedulerLock(name = "registryRefresh", lockAtMostFor = "3m")
  public void refresh() {

    // yes, i know the time is recorded via micrometer already, but
    // as a user, i'd like to see the overall time in the logs as well.

    log.debug("Triggering refresh on " + registry.getClass().getSimpleName());
    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      registry.refresh();
    } finally {
      stopwatch.stop();
      log.debug(
          "Refresh on {} took {}ms",
          registry.getClass().getSimpleName(),
          stopwatch.elapsed().toMillis());
    }
  }
}
