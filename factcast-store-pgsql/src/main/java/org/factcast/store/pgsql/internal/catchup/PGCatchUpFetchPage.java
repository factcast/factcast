package org.factcast.store.pgsql.internal.catchup;

import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.rowmapper.PGFactExtractor;
import org.factcast.store.pgsql.internal.rowmapper.PGUUIDExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PGCatchUpFetchPage {
    final JdbcTemplate jdbc;

    final SubscriptionRequestTO req;

    final long clientId;

    final long pageSize;

    // use LinkedLists so that we can use remove() rather than iteration, in
    // order to release Facts for GC asap.
    public LinkedList<Fact> fetchFacts(AtomicLong serial) {

        final PreparedStatementSetter pss = ps -> {
            ps.setLong(1, clientId);
            ps.setLong(2, serial.get());
            ps.setLong(3, pageSize);
        };

        log.trace("{}  fetching next page for cid={}, limit={}, ser>{}", req, clientId, pageSize,
                serial.get());
        return new LinkedList<Fact>(jdbc.query(PGConstants.SELECT_FACT_FROM_CATCHUP, pss,
                new PGFactExtractor(serial)));
    }

    // use LinkedLists so that we can use remove() rather than iteration, in
    // order to release Facts for GC asap.
    public LinkedList<UUID> fetchIDs(AtomicLong serial) {
        if (true) {
            throw new UnsupportedOperationException("TODO");
        }

        final PreparedStatementSetter pss = ps -> {
            ps.setLong(1, clientId);
            ps.setLong(2, serial.get());
            ps.setLong(3, pageSize);
        };

        log.trace("{}  fetching for cid={} next {} ids", req, clientId, pageSize);
        return new LinkedList<>(jdbc.query(PGConstants.SELECT_ID_FROM_CATCHUP, pss,
                new PGUUIDExtractor(serial)));
    }
}
