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
package org.factcast.store.internal.catchup.tmppaged;

import com.google.common.base.Stopwatch;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.PgConstants;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.rowmapper.PgFactExtractor;
import org.springframework.jdbc.core.JdbcTemplate;

@Slf4j
@RequiredArgsConstructor
public class PgCatchUpFetchTmpPage {

  @NonNull final JdbcTemplate jdbc;

  final int pageSize;

  @NonNull final SubscriptionRequestTO req;

  @NonNull final CurrentStatementHolder statementHolder;

  public List<Fact> fetchFacts(@NonNull AtomicLong serial) {
    Stopwatch sw = Stopwatch.createStarted();
    List<Fact> list =
        jdbc.query(
            PgConstants.SELECT_FACT_FROM_CATCHUP,
            ps -> {
              ps.setLong(1, serial.get());
              ps.setLong(2, pageSize);
              statementHolder.statement(ps);
            },
            new PgFactExtractor(serial));
    sw.stop();
    log.trace(
        "{} fetched next page of Facts limit={}, ser>{} in {}ms",
        req,
        pageSize,
        serial.get(),
        sw.elapsed(TimeUnit.MILLISECONDS));
    return list;
  }
}
