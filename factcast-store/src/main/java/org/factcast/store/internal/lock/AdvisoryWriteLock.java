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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;

@SuppressWarnings("java:S2077")
public class AdvisoryWriteLock implements FactTableWriteLock {
  private final JdbcTemplate jdbc;

  public AdvisoryWriteLock(JdbcTemplate jdbc) {
    this.jdbc = new JdbcTemplate(jdbc.getDataSource());
    // TODO maybe a good place to set a query timeout?
  }

  @Override
  public void acquireExclusiveLock() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_lock(" + AdvisoryLocks.PUBLISH.code() + ")");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void releaseExclusiveLock() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_unlock(" + AdvisoryLocks.PUBLISH.code() + ")");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void acquireSharedTxLock() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_xact_lock_shared(" + AdvisoryLocks.PUBLISH.code() + ")");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void acquireExclusiveTxLock() {
    // TODO add metrics
    jdbc.execute("SELECT pg_advisory_xact_lock(" + AdvisoryLocks.PUBLISH.code() + ")");
  }
}
