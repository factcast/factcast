package org.factcast.store.pgsql.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.google.common.eventbus.EventBus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// TODO split
// TODO document properly
@Slf4j
@RequiredArgsConstructor
class PGSubscription {

	private final JdbcTemplate jdbcTemplate;
	private final EventBus eventBus;
	private final PGFactIdToSerMapper idToSerMapper;

	private final AtomicLong serial = new AtomicLong(0);
	private final AtomicBoolean disconnected = new AtomicBoolean(false);
	private final AtomicInteger count = new AtomicInteger(0);
	private final AtomicInteger hit = new AtomicInteger(0);

	private CondensedExecutor condensedExecutor;

	Subscription run(SubscriptionRequestTO request, FactStoreObserver observer) {
		log.trace("initializing for {}", request);

		PGQueryBuilder q = new PGQueryBuilder(request);

		initializeSerialToStartAfter(request);

		String sql = q.createSQL();
		PreparedStatementSetter setter = q.createStatementSetter(serial);
		RowCallbackHandler rsHandler = new FactRowCallbackHandler(observer, new PGPostQueryMatcher(request.specs()));

		SynchronizedQuery query = new SynchronizedQuery(sql, setter, rsHandler);
		return catchupAndFollow(request, observer, query);
	}

	@RequiredArgsConstructor
	private class FactRowCallbackHandler implements RowCallbackHandler {

		final FactStoreObserver observer;
		final PGPostQueryMatcher postQueryMatcher;

		@Override
		public void processRow(ResultSet rs) throws SQLException {
			if (isConnected()) {
				Fact f = PGFact.from(rs);
				count.incrementAndGet();
				final UUID factId = f.id();

				if (postQueryMatcher.test(f)) {
					hit.incrementAndGet();
					try {
						observer.onNext(f);
					} catch (Throwable e) {
						log.warn("Exception from observer. THIS IS A BUG! Please Report!", e);
						disconnect(observer);
					}
					log.trace("onNext called with id={}", factId);
				} else {
					log.trace("filtered id={}", factId);
				}
				serial.set(rs.getLong(PGConstants.COLUMN_SER));
			}
		}
	}

	@RequiredArgsConstructor
	private class SynchronizedQuery implements Runnable {

		final String sql;
		final PreparedStatementSetter setter;
		final RowCallbackHandler rowHandler;

		@Override
		// the synchronized here is VERY important!
		public synchronized void run() {
			jdbcTemplate.query(sql, setter, rowHandler);
		}

	}

	private void initializeSerialToStartAfter(SubscriptionRequestTO request) {
		Long startingSerial = request.startingAfter().map(idToSerMapper::retrieve).orElse(0L);
		serial.set(startingSerial);
		log.trace("starting to stream from id: {}", startingSerial);
	}

	private Subscription catchupAndFollow(SubscriptionRequest request, FactStoreObserver factStoreObserver,
			SynchronizedQuery query) {
		long start = System.currentTimeMillis();

		if (request.ephemeral()) {
			this.serial.set(getLatestFactSer());
		} else {
			catchup(query);
		}

		// propagate catchup
		if (isConnected()) {
			log.trace("signaling catchup");
			factStoreObserver.onCatchup();
			log.info("Catchup stats: runtime:{}ms, hitRate:{}% (count:{}, hit:{}), highwater:{} ",
					System.currentTimeMillis() - start, hitRate(), count.get(), hit.get(), serial.get());
		}

		if (isConnected() && request.continous()) {

			log.info("Entering follow mode for {}", request);
			count.set(0);
			hit.set(0);

			long delayInMs;

			if (request.maxBatchDelayInMs() < 1) {
				// ok, instant query after NOTIFY
				delayInMs = 0;
			} else {
				// spread consumers, so that they query at different points in
				// time, even if they get triggered at the same PIT, and share
				// the
				// same latency requirements
				//
				// ok, that is unlikely to be necessary, but easy to do, so...
				delayInMs = (((request.maxBatchDelayInMs() / 4L) * 3L)
						+ (long) (Math.abs(Math.random() * ((request.maxBatchDelayInMs() / 4)))));
				log.info("Setting delay for this instance to " + delayInMs + ", maxDelay was "
						+ request.maxBatchDelayInMs());
			}

			this.condensedExecutor = new CondensedExecutor(delayInMs, query, () -> isConnected());
			eventBus.register(condensedExecutor);
			// catchup phase 3 – make sure, we did not miss any fact due to
			// slow registration
			condensedExecutor.trigger();

			return () -> {
				disconnect(factStoreObserver);
			};

		} else {
			log.debug("Complete");
			factStoreObserver.onComplete();
			// FIXME disc.?
			return this::nop;
		}
	}

	private void disconnect(FactStoreObserver c) {
		log.info("Disconnecting");
		disconnected.set(true);

		if (condensedExecutor != null) {
			condensedExecutor.cancel();
			eventBus.unregister(condensedExecutor);
		}

		log.info("Follow stats: hitRate:{}% (count:{}, hit:{}), highwater:{} ", hitRate(), count.get(), hit.get(),
				serial.get());
		log.info("Disconnected");

		// TODO strategic decision: consumer.onComplete(); after
		// cancel!?
		log.info("Complete");
		try {
			c.onComplete();
		} catch (Throwable e) {
			log.trace("Closing observer not possible. Ignoring", e);
		}
	}

	private void catchup(SynchronizedQuery query) {
		// catchup phase 1 – historic facts
		if (isConnected()) {
			log.trace("catchup phase1 - historic Facts");
			query.run();
		}
		// catchup phase 2 (all since connect)
		if (isConnected()) {
			log.trace("catchup phase2 - Facts since connect");
			query.run();
		}
	}

	private boolean isConnected() {
		return !disconnected.get();
	}

	private long getLatestFactSer() {
		return jdbcTemplate.queryForObject(PGConstants.SELECT_LATEST_SER, Long.class).longValue();
	}

	private long hitRate() {
		if (count.get() == 0) {
			return 100;
		}
		return Math.round((100.0 / count.get()) * hit.get());
	}

	private void nop() {
	}

}
