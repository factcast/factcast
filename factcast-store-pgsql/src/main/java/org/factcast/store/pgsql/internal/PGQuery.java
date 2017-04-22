package org.factcast.store.pgsql.internal;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactSpec;
import org.factcast.core.subscription.FactSpecMatcher;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.store.pgsql.internal.PGListener.FactInsertionEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowCallbackHandler;

import com.google.common.collect.Lists;
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
	private final PGFactFactory factory;

	private final AtomicLong ser = new AtomicLong(0);
	private final AtomicBoolean disconnected = new AtomicBoolean(false);
	private CondensedExecutor cExec;
	private Runnable query;
	private final AtomicInteger count = new AtomicInteger(0);
	private final AtomicInteger hit = new AtomicInteger(0);
	private Predicate<Fact> postQueryMatcher;

	Subscription catchup(SubscriptionRequest req, FactStoreObserver observer) {

		log.trace("catching up for " + req);

		List<FactSpec> specs = Lists.newArrayList(req.specs());
		specs.add(0, FactSpec.forMark());

		if (hasAnyScriptFilters(req)) {
			postQueryMatcher = FactSpecMatcher.matchesAnyOf(specs);
		} else {
			log.trace("post query filtering has been disabled");
		}

		Long staringSerial = req.startingAfter().map(serMapper::retrieve).orElse(0L);
		ser.set(staringSerial);

		log.trace("catching up from {}", staringSerial);

		PGQueryBuilder q = new PGQueryBuilder(specs);
		String sql = q.createSQL();
		PreparedStatementSetter setter = q.createStatementSetter(ser);

		RowCallbackHandler rsHandler = rs -> {
			if (!disconnected.get()) {
				Fact f = factory.extractData(rs);
				count.incrementAndGet();
				log.trace("found potential match {}", f.id());
				// intentionally using short circ. || here
				if ((postQueryMatcher == null) || postQueryMatcher.test(f)) {
					hit.incrementAndGet();
					observer.onNext(f);
					log.trace("onNext called with id={}", f.id());
				} else {
					log.trace("filtered id={}", f.id());
				}
				this.ser.set(rs.getLong(PGConstants.COLUMN_SER));
			}
		};

		query = () -> {
			tpl.query(sql, setter, rsHandler);
		};

		return catchupAndFollow(req, observer);
	}

	private boolean hasAnyScriptFilters(SubscriptionRequest req) {
		return req.specs().stream().anyMatch(s -> s.jsFilterScript() != null);
	}

	private Subscription catchupAndFollow(SubscriptionRequest req, FactStoreObserver c) {
		long start = System.currentTimeMillis();
		// catchup phase 1 – historic facts
		if (!disconnected.get()) {
			log.trace("catchup phase1 - historic Facts");
			query.run();
		}
		// catchup phase 2 (all since connect)
		if (!disconnected.get()) {
			log.trace("catchup phase2 - Facts since connect");
			query.run();
		}
		// propagate catchup
		if (!disconnected.get()) {
			log.trace("signaling catchup");
			c.onCatchup();
			log.info("Catchup stats: runtime:{}ms, hitRate:{}% (count:{}, hit:{}), highwater:{} ",
					System.currentTimeMillis() - start, hitRate(), count.get(), hit.get(), ser.get());
		}
		if (!disconnected.get() && req.continous()) {

			log.info("Entering follow mode for {}", req);
			count.set(0);
			hit.set(0);

			// spread consumers, so that they query at different points in time,
			// even if they get triggered at the same PIT, and share the same
			// latency requirements
			long delay = (((req.maxLatencyInMillis() / 4L) * 3L)
					+ (long) (Math.abs(Math.random() * ((req.maxLatencyInMillis() / 4)))));
			log.debug("setting delay for this instance to " + delay + ", maxDelay was " + req.maxLatencyInMillis());
			cExec = new CondensedExecutor(delay, query);
			bus.register(this);
			// catchup phase 3 – make sure, we did not miss any fact due to
			// slow registration
			if (!disconnected.get()) {
				cExec.trigger();
			}
			return () -> {
				log.info("Disconnecting");
				disconnected.set(true);
				if (cExec != null) {
					cExec.cancel();
				}
				bus.unregister(this);
				log.info("Follow stats: hitRate:{}% (count:{}, hit:{}), highwater:{} ", hitRate(), count.get(),
						hit.get(), ser.get());
				log.info("Disconnected");

				// TODO strategic decision: consumer.onComplete(); after
				// cancel!?
				log.info("Complete");
				c.onComplete();
			};

		} else {
			log.debug("Complete");
			c.onComplete();
			return this::nop;
		}
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
		if (!disconnected.get()) {
			if (cExec != null) {
				cExec.trigger();
			}
		}
	}

}
