package org.factcast.store.pgsql.internal;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactSpecMatcher;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.store.pgsql.internal.PGListener.FactInsertionEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// TODO document properly
@Slf4j
@RequiredArgsConstructor
class PGQuery {

	private final JdbcTemplate tpl;
	private final EventBus bus;
	private final PGFactIdToSerMapper serMapper;

	private final AtomicLong ser = new AtomicLong(0);
	private final AtomicBoolean disconnected = new AtomicBoolean(false);
	private CondensedExecutor cExec;
	private Runnable query;
	private final AtomicInteger count = new AtomicInteger(0);
	private final AtomicInteger hit = new AtomicInteger(0);
	private Predicate<Fact> postQueryMatcher;

	Subscription run(SubscriptionRequestTO req, FactStoreObserver observer) {

		log.trace("initializing for {}", req);

		if (req.hasAnyScriptFilters()) {
			postQueryMatcher = FactSpecMatcher.matchesAnyOf(req.specs());
		} else {
			log.trace("post query filtering has been disabled");
		}

		Long staringSerial = req.startingAfter().map(serMapper::retrieve).orElse(0L);
		ser.set(staringSerial);

		log.trace("initializing from {}", staringSerial);

		PGQueryBuilder q = new PGQueryBuilder(req);
		String sql = q.createSQL();
		log.trace("subscription sql={}", sql);
		PreparedStatementSetter setter = q.createStatementSetter(ser);

		RowCallbackHandler rsHandler = rs -> {
			if (isConnected()) {
				Fact f = PGFact.from(rs);
				count.incrementAndGet();
				final UUID factId = f.id();
				if (postQueryMatcher != null) {
					log.trace("found potential match {}", factId);
				}
				// intentionally using short circ. || here
				if ((postQueryMatcher == null) || postQueryMatcher.test(f)) {
					hit.incrementAndGet();
					try {
						observer.onNext(f);
					} catch (Throwable e) {
						log.warn("Exception from observer", e);
						disconnect(observer);

					}
					log.trace("onNext called with id={}", factId);
				} else {
					log.trace("filtered id={}", factId);
				}
				this.ser.set(rs.getLong(PGConstants.COLUMN_SER));
			}
		};

		query = () -> {
			synchronized (PGQuery.this) {
				tpl.query(sql, setter, rsHandler);
			}
		};

		return catchupAndFollow(req, observer);
	}

	private Subscription catchupAndFollow(SubscriptionRequest req, FactStoreObserver c) {
		long start = System.currentTimeMillis();

		if (req.ephemeral()) {
			this.ser.set(getLatestFactSer());
		} else {
			catchup();
		}

		// propagate catchup
		if (isConnected()) {
			log.trace("signaling catchup");
			c.onCatchup();
			log.info("Catchup stats: runtime:{}ms, hitRate:{}% (count:{}, hit:{}), highwater:{} ",
					System.currentTimeMillis() - start, hitRate(), count.get(), hit.get(), ser.get());
		}

		if (isConnected() && req.continous()) {

			log.info("Entering follow mode for {}", req);
			count.set(0);
			hit.set(0);

			if (req.maxBatchDelayInMs() < 1) {
				// ok, instant query after NOTIFY
				cExec = new CondensedExecutor(query);
			} else {
				// spread consumers, so that they query at different points in
				// time,
				// even if they get triggered at the same PIT, and share the
				// same
				// latency requirements
				long delay = (((req.maxBatchDelayInMs() / 4L) * 3L)
						+ (long) (Math.abs(Math.random() * ((req.maxBatchDelayInMs() / 4)))));
				log.info("Setting delay for this instance to " + delay + ", maxDelay was " + req.maxBatchDelayInMs());

				cExec = new CondensedExecutor(delay, query);
			}

			bus.register(this);
			// catchup phase 3 – make sure, we did not miss any fact due to
			// slow registration
			if (isConnected()) {
				cExec.trigger();
			}
			return () -> {
				disconnect(c);
			};

		} else {
			log.debug("Complete");
			c.onComplete();
			return this::nop;
		}
	}

	private void disconnect(FactStoreObserver c) {
		log.info("Disconnecting");
		disconnected.set(true);
		if (cExec != null) {
			cExec.cancel();
		}
		bus.unregister(this);
		log.info("Follow stats: hitRate:{}% (count:{}, hit:{}), highwater:{} ", hitRate(), count.get(), hit.get(),
				ser.get());
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

	private void catchup() {
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
		return tpl.queryForObject(PGConstants.SELECT_LATEST_SER, Long.class).longValue();
	}

	private long hitRate() {
		if (count.get() == 0) {
			return 100;
		}
		return Math.round((100.0 / count.get()) * hit.get());
	}

	private void nop() {
	}

	@Subscribe
	public void onEvent(FactInsertionEvent ev) {
		if (isConnected()) {
			if (cExec != null) {
				cExec.trigger();
			}
		}
	}

}
