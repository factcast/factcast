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

@RequiredArgsConstructor
@Slf4j
public class PGCatchupPrepare {

    final JdbcTemplate jdbc;

    final SubscriptionRequestTO req;

    long prepareCatchup(AtomicLong serial) {
        PGQueryBuilder b = new PGQueryBuilder(req);

        long clientId = jdbc.queryForObject(PGConstants.NEXT_FROM_CATCHUP_SEQ, Long.class);

        String catchupSQL = b.catchupSQL(clientId);

        jdbc.execute(catchupSQL, new PreparedStatementCallback<Void>() {

            @Override
            public Void doInPreparedStatement(PreparedStatement ps) throws SQLException,
                    DataAccessException {
                b.createStatementSetter(serial).setValues(ps);
                int numberOfFactsToCatchup = ps.executeUpdate();
                // TODO
                log.info("{} Catchup prep [{},) for cid={} found {} facts. Prepared Paging.", req,
                        serial, clientId, numberOfFactsToCatchup);
                return null;
            }
        });

        return clientId;
    }

}
