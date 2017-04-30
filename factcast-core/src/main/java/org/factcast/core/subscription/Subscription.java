package org.factcast.core.subscription;

import java.util.concurrent.TimeoutException;

/**
 * A handle that is returned from Subscribe Methods in order to be able to
 * signal, that a client lost its interest in the stream.
 * 
 * This might be used for resource-efficiency.
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
/**
 * @author <uwe.schaefer@mercateo.com>
 *
 */
public interface Subscription extends AutoCloseable {

    /**
     * blocks until Catchup or Cancelled event received
     * 
     * @return this
     * @throws SubscriptionCancelledException
     */
    Subscription awaitCatchup() throws SubscriptionCancelledException;

    /**
     * blocks until Catchup or Cancelled event received
     * 
     * @param waitTimeInMillis
     * @return this
     * @throws SubscriptionCancelledException
     * @throws TimeoutException
     *             if no relevant event was received in time
     */
    Subscription awaitCatchup(long waitTimeInMillis) throws SubscriptionCancelledException,
            TimeoutException;

    /**
     * blocks until Complete or Cancelled event received
     * 
     * @return
     * @throws SubscriptionCancelledException
     */
    Subscription awaitComplete() throws SubscriptionCancelledException;

    /**
     * blocks until Complete or Cancelled event received
     * 
     * @param waitTimeInMillis
     * @return this
     * @throws SubscriptionCancelledException
     * @throws TimeoutException
     *             if no relevant event was received in time
     */
    Subscription awaitComplete(long waitTimeInMillis) throws SubscriptionCancelledException,
            TimeoutException;

}
