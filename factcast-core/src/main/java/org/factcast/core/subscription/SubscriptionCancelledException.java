package org.factcast.core.subscription;

/**
 * Signals, that a Subscription.wait* method cannot terminate normally.
 * 
 * @author <uwe.schaefer@mercateo.com>
 *
 */
public class SubscriptionCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SubscriptionCancelledException(Throwable e) {
        super(e);
    }

    public SubscriptionCancelledException(String msg, Throwable e) {
        super(msg, e);
    }

    public SubscriptionCancelledException(String msg) {
        super(msg);
    }
}
