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
package org.factcast.store.internal.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@SuppressWarnings("java:S2077")
public class AdvisoryWriteLock implements FactTableWriteLock {
  private static final String LOCK_SHARED_SQL =
      "SELECT pg_advisory_xact_lock_shared(" + AdvisoryLocks.PUBLISH.code() + ")";
  private static final String LOCK_EXCLUSIVE_SQL =
      "SELECT pg_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")";

  private final JdbcTemplate tpl;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void acquireSharedTXLock() {
    tpl.execute(LOCK_SHARED_SQL);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void acquireExclusiveTXLock() {
    tpl.execute(LOCK_EXCLUSIVE_SQL);
  }
}
