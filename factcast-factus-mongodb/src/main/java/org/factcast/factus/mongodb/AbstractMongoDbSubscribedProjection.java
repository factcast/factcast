/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.factus.mongodb;

import com.mongodb.client.MongoDatabase;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.factcast.factus.projection.SubscribedProjection;
import org.springframework.scheduling.annotation.Scheduled;

@Slf4j
public abstract class AbstractMongoDbSubscribedProjection extends AbstractMongoDbProjection
    implements SubscribedProjection {

  protected AbstractMongoDbSubscribedProjection(@NonNull MongoDatabase mongoDb) {
    super(mongoDb);
  }

  // Must be smaller than MAX_LEASE_DURATION_SECONDS
  @Scheduled(cron = "*/30 * * * * *")
  private void refreshWriterToken() {
    if (this.lock() != null) {
      try {
        Optional<SimpleLock> extendedLock =
            this.lock().extend(MAX_LEASE_DURATION_SECONDS, MIN_LEASE_DURATION_SECONDS);
        this.lock(extendedLock.orElse(null));
        log.debug(
            "{} to extend lock for projection: {}",
            extendedLock.isPresent() ? "Succeeded" : "Failed",
            projectionKey);
      } catch (IllegalStateException e) {
        log.debug(
            "Failed to extend lock during keep-alive: {}. Might have been extended manually.",
            e.getMessage());
      }
    }
  }
}
