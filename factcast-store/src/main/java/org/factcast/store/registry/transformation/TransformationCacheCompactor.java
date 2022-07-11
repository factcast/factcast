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
package org.factcast.store.registry.transformation;

import com.google.common.base.Stopwatch;
import java.time.ZonedDateTime;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.factcast.store.registry.transformation.cache.TransformationCache;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
@Slf4j
public class TransformationCacheCompactor {
  @NonNull final TransformationCache cache;

  final int days;

  @Scheduled(cron = "${factcast.store.transformationCacheCompactCron:0 0 0 * * *}")
  @SchedulerLock(name = "transformationCacheCompact", lockAtMostFor = "PT1h")
  public void compact() {

    // yes, i know the time is recorded via micrometer already, but
    // as a user, i'd like to see the overall time in the logs as well.

    log.debug("Triggering compact on {}", cache.getClass().getSimpleName());
    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      cache.compact(ZonedDateTime.now().minusDays(days));
    } finally {
      stopwatch.stop();
      log.debug(
          "Compaction on {} took {}ms",
          cache.getClass().getSimpleName(),
          stopwatch.elapsed().toMillis());
    }
  }
}
