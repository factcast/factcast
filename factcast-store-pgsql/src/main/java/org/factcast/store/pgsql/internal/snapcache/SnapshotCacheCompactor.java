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
package org.factcast.store.pgsql.internal.snapcache;

import org.factcast.store.pgsql.internal.PgMetrics;
import org.factcast.store.pgsql.internal.PgMetrics.StoreMetrics.OP;
import org.joda.time.DateTime;
import org.springframework.scheduling.annotation.Scheduled;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SchedulerLock;

@RequiredArgsConstructor
@Slf4j
public class SnapshotCacheCompactor {

    @NonNull
    final SnapshotCache cache;

    @NonNull
    final PgMetrics pgMetrics;

    final int days;

    @Scheduled(
            cron = "${factcast.store.pgsql.snapshotCacheCompactCron:0 0 0 * * *}")
    @SchedulerLock(name = "snapshotCacheCompact", lockAtMostFor = 1000 * 60 * 60)
    public void compact() {
        pgMetrics.time(OP.COMPACT_SNAPSHOT_CACHE, () -> cache.compact(DateTime.now()
                .minusDays(days)));
    }
}
