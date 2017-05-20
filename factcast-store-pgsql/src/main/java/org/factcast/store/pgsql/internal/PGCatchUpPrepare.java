package org.factcast.store.pgsql.internal;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.subscription.SubscriptionRequestTO;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCallback;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Copies all matching SERs from fact to the catchup table, in order to be able
 * to page effectively, without repeatingly doing the index scan.
 * 
 * @author <uwe.schaefer@mercateo.com>
 *
 */
@RequiredArgsConstructor
@Slf4j
public class PGCatchUpPrepare {

    final JdbcTemplate jdbc;

    final SubscriptionRequestTO req;

    long prepareCatchup(AtomicLong serial) {
        PGQueryBuilder b = new PGQueryBuilder(req);
        long clientId = jdbc.queryForObject(PGConstants.NEXT_FROM_CATCHUP_SEQ, Long.class);
        String catchupSQL = b.catchupSQL(clientId);
        return jdbc.execute(catchupSQL, new PreparedStatementCallback<Long>() {

            @Override
            public Long doInPreparedStatement(PreparedStatement ps) throws SQLException,
                    DataAccessException {
                log.debug("{} preparing paging for matches after {}", req, serial.get());

                b.createStatementSetter(serial).setValues(ps);
                int numberOfFactsToCatchup = ps.executeUpdate();

                if (numberOfFactsToCatchup > 0) {
                    log.debug("{} prepared {} facts for cid={}", req, numberOfFactsToCatchup,
                            clientId);
                    return clientId;
                } else {
                    log.debug("{} nothing to catch up", req);
                    return 0L;
                }
            }
        });

    }

}
