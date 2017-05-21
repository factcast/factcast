package org.factcast.store.pgsql.internal.catchup;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.PGConfigurationProperties;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.rowmapper.PGFactExtractor;
import org.factcast.store.pgsql.internal.rowmapper.PGIdFactExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PGCatchUpFetchPage {

    final JdbcTemplate jdbc;

    final PGConfigurationProperties properties;

    final SubscriptionRequestTO req;

    final long clientId;

    // use LinkedLists so that we can use remove() rather than iteration, in
    // order to release Facts for GC asap.
    public LinkedList<Fact> fetchFacts(AtomicLong serial) {

        int factPageSize = properties.getFactPageSize();
        log.trace("{}  fetching next page of Facts for cid={}, limit={}, ser>{}", req, clientId,
                factPageSize, serial.get());
        return new LinkedList<Fact>(jdbc.query(PGConstants.SELECT_FACT_FROM_CATCHUP, createSetter(
                serial, factPageSize), new PGFactExtractor(serial)));
    }

    private PreparedStatementSetter createSetter(AtomicLong serial, int pageSize) {
        return ps -> {
            ps.setLong(1, clientId);
            ps.setLong(2, serial.get());
            ps.setLong(3, pageSize);
        };
    }

    // use LinkedLists so that we can use remove() rather than iteration, in
    // order to release Facts for GC asap.
    public LinkedList<Fact> fetchIdFacts(AtomicLong serial) {
        int idPageSize = properties.getIdPageSize();
        log.trace("{}  fetching next page of Ids for cid={}, limit={}, ser>{}", req, clientId,
                idPageSize, serial.get());

        return new LinkedList<>(jdbc.query(PGConstants.SELECT_ID_FROM_CATCHUP, createSetter(serial,
                idPageSize), new PGIdFactExtractor(serial)));
    }
}
