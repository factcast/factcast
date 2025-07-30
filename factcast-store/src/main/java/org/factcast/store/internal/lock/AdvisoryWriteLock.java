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
public class AdvisoryWriteLock implements FactTableWriteLock {
  private final JdbcTemplate tpl;

  @Override
  @Deprecated
  @Transactional(propagation = Propagation.MANDATORY)
  public void aquireExclusiveTXLock() {
    aquireExclusiveTXLock(AdvisoryLocks.PUBLISH.code());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void aquireExclusiveTXLock(int code) {
    tpl.execute("SELECT pg_advisory_xact_lock(" + code + ")");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void aquireSharedTXLock(int code) {
    tpl.execute("SELECT pg_advisory_xact_lock_shared(" + code + ")");
  }
}
