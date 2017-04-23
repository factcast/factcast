package org.factcast.store.pgsql.internal;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a given runnable if triggered, but ignores all subsequent triggers
 * for maxDelayInMillis.
 * 
 * @author usr
 *
 */
@Slf4j
@RequiredArgsConstructor
@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__(@VisibleForTesting))
class CondensedExecutor {

	private final long maxDelayInMillis;
	private final Runnable target;

	private Timer timer = new Timer(CondensedExecutor.class.getSimpleName() + ".timer", true);
	private final AtomicBoolean currentlyScheduled = new AtomicBoolean(false);

	public void trigger() {
		if (maxDelayInMillis < 1) {
			executeQuery();
		} else if (!currentlyScheduled.getAndSet(true)) {
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					currentlyScheduled.set(false);
					CondensedExecutor.this.executeQuery();
				}
			}, maxDelayInMillis);
		}
	}

	protected void executeQuery() {
		try {
			target.run();
		} catch (Throwable e) {
			log.error("cannot execute query: " + e);
		}
	}

	public void cancel() {
		timer.cancel();
		currentlyScheduled.set(true);
		timer.purge();
	}

	public CondensedExecutor(Runnable query) {
		this(0, query);
	}
}
