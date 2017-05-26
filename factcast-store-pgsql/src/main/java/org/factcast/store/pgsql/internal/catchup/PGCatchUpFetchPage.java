package org.factcast.store.pgsql.internal.catchup;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.PGConstants;
import org.factcast.store.pgsql.internal.rowmapper.PGFactExtractor;
import org.factcast.store.pgsql.internal.rowmapper.PGIdFactExtractor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

import com.google.common.base.Stopwatch;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PGCatchUpFetchPage {
    @NonNull
    final JdbcTemplate jdbc;

    final int pageSize;

    @NonNull
    final SubscriptionRequestTO req;

    final long clientId;

    // use LinkedLists so that we can use remove() rather than iteration, in
    // order to release Facts for GC asap.
    public LinkedList<Fact> fetchFacts(@NonNull AtomicLong serial) {
        Stopwatch sw = Stopwatch.createStarted();
        final LinkedList<Fact> list = new LinkedList<Fact>(jdbc.query(
                PGConstants.SELECT_FACT_FROM_CATCHUP, createSetter(serial, pageSize),
                new PGFactExtractor(serial)));
        sw.stop();
        log.debug("{}  fetched next page of Facts for cid={}, limit={}, ser>{} in {}ms", req,
                clientId, pageSize, serial.get(), sw.elapsed(TimeUnit.MILLISECONDS));
        return list;
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
    public LinkedList<Fact> fetchIdFacts(@NonNull AtomicLong serial) {

        Stopwatch sw = Stopwatch.createStarted();
        final LinkedList<Fact> list = new LinkedList<>(jdbc.query(
                PGConstants.SELECT_ID_FROM_CATCHUP, createSetter(serial, pageSize),
                new PGIdFactExtractor(serial)));
        sw.stop();
        log.debug("{}  fetched next page of Ids for cid={}, limit={}, ser>{} in {}ms", req,
                clientId, pageSize, serial.get(), sw.elapsed(TimeUnit.MILLISECONDS));

        return list;
    }

}
