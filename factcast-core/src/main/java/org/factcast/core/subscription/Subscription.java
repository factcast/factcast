/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.factcast.core.subscription;

import java.util.concurrent.TimeoutException;

/**
 * @author <uwe.schaefer@mercateo.com>
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
