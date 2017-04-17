package org.factcast.store.pgsql.internal;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.common.annotations.VisibleForTesting;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED, onConstructor = @__(@VisibleForTesting))
class CondensedExecutor {

	private final long maxDelayInMillis;
	private final Runnable target;

	private Timer timer = new Timer(CondensedExecutor.class.getSimpleName() + ".timer", true);
	private final AtomicBoolean currentlyScheduled = new AtomicBoolean(false);

	public void trigger() {
		if (!currentlyScheduled.getAndSet(true)) {
			timer.schedule(new TimerTask() {

				@Override
				public void run() {
					currentlyScheduled.set(false);
					synchronized (target) {
						target.run();
					}
				}
			}, maxDelayInMillis);
		}
	}

	public void cancel() {
		timer.cancel();
		currentlyScheduled.set(true);
		timer.purge();
	}
}
