package org.factcast.store.pgsql.internal;

import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;

/**
 * tiny statistics class that keeps track of the amount of hits vs filtered
 * Facts, as well as a start-time.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Slf4j
class PGFilteringStats {
    long start = System.currentTimeMillis();

    final AtomicLong count = new AtomicLong(0);

    final AtomicLong hit = new AtomicLong(0);

    long rate() {
        if (count.get() == 0) {
            return 100;
        }
        return Math.round((100.0 / count.get()) * hit.get());
    }

    void dump() {
        log.info("Subscription stats: hitRate:{}% (count:{}, hit:{})", rate(), count.get(), hit
                .get());
        reset();
    }

    void dumpForCatchup() {
        log.info("Catchup stats: elapsed:{}ms, hitRate:{}% (count:{}, hit:{})", elapsedMillis(),
                rate(), count.get(), hit.get());
        reset();
    }

    void reset() {
        hit.set(0);
        count.set(0);
        start = System.currentTimeMillis();
    }

    void notifyHit() {
        hit.incrementAndGet();
    }

    void notifyCount() {
        count.incrementAndGet();
    }

    long elapsedMillis() {
        return System.currentTimeMillis() - start;
    }
}