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
package org.factcast.store.internal.snapcache;

import java.time.ZonedDateTime;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.store.internal.PgMetrics;
import org.factcast.store.internal.StoreMetrics;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
// Even though it uses PgMetrics (which might be renamed to StoreMetrics in the future) it is not
// specific to Pg or PgSnapshotCache
public class SnapshotCacheCompactor {

  @NonNull final SnapshotCache cache;

  @NonNull final PgMetrics pgMetrics;

  final int days;

  @Scheduled(cron = "${factcast.store.snapshotCacheCompactCron:0 0 0 * * *}")
  @SchedulerLock(name = "snapshotCacheCompact", lockAtMostFor = "PT1h")
  public void compact() {
    pgMetrics.time(
        StoreMetrics.OP.COMPACT_SNAPSHOT_CACHE,
        () -> cache.compact(ZonedDateTime.now().minusDays(days)));
  }
}
