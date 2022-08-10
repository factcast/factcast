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
import java.sql.SQLException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.internal.query.CurrentStatementHolder;
import org.factcast.store.internal.query.PgQueryBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

/**
 * Copies all matching SERs from fact to the catchup table, in order to be able to page effectively,
 * without repeatingly doing the index scan.
 *
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@RequiredArgsConstructor
@Slf4j
public class PgCatchUpPrepare {

  final JdbcTemplate jdbc;

  final SubscriptionRequestTO req;

  final CurrentStatementHolder statementHolder;

  @SuppressWarnings("ConstantConditions")
  public long prepareCatchup(AtomicLong serial) {
    PgQueryBuilder b = new PgQueryBuilder(req.specs(), statementHolder);
    try {
      String catchupSQL = b.catchupSQL();
      PreparedStatementCallback<Long> callback =
          ps -> {
            log.debug("{} preparing paging for matches after {}", req, serial.get());
            try {
              Stopwatch sw = Stopwatch.createStarted();
              b.createStatementSetter(serial).setValues(ps);
              long numberOfFactsToCatchup = ps.executeUpdate();
              sw.stop();
              if (numberOfFactsToCatchup > 0) {
                log.debug(
                    "{} prepared {} facts in {}ms",
                    req,
                    numberOfFactsToCatchup,
                    sw.elapsed(TimeUnit.MILLISECONDS));
                return numberOfFactsToCatchup;
              } else {
                log.debug("{} nothing to catch up", req);
                return 0L;
              }
            } catch (SQLException ex) {
              log.error(req + " While trying to prepare catchup", ex);
              throw ex;
            }
          };
      return jdbc.execute(catchupSQL, callback);
    } finally {
      statementHolder.statement(null);
    }
  }
}
