/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql.internal.catchup;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.query.PGQueryBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import com.google.common.base.Stopwatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies all matching SERs from fact to the catchup table, in order to be able
 * to page effectively, without repeatingly doing the index scan.
 *
 * @author <uwe.schaefer@mercateo.com>
 */
@RequiredArgsConstructor
@Slf4j
public class PGCatchUpPrepare {

    final JdbcTemplate jdbc;

    final SubscriptionRequestTO req;

    public long prepareCatchup(AtomicLong serial) {
        PGQueryBuilder b = new PGQueryBuilder(req);
        long clientId = jdbc.queryForObject(PGConstants.NEXT_FROM_CATCHUP_SEQ, Long.class);
        String catchupSQL = b.catchupSQL(clientId);
        return jdbc.execute(catchupSQL, (PreparedStatementCallback<Long>) ps -> {
            log.debug("{} preparing paging for matches after {}", req, serial.get());
            try {
                Stopwatch sw = Stopwatch.createStarted();
                b.createStatementSetter(serial).setValues(ps);
                int numberOfFactsToCatchup = ps.executeUpdate();
                sw.stop();
                if (numberOfFactsToCatchup > 0) {
                    log.debug("{} prepared {} facts for cid={} in {}ms", req,
                            numberOfFactsToCatchup, clientId, sw.elapsed(TimeUnit.MILLISECONDS));
                    return clientId;
                } else {
                    log.debug("{} nothing to catch up", req);
                    return 0L;
                }
            } catch (SQLException ex) {
                log.error("While trying to prepare catchup", ex);
                throw ex;
            }
        });
    }
}
