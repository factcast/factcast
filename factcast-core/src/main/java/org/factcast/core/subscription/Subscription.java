/*
 * Copyright © 2017-2020 factcast.org
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

/** @author <uwe.schaefer@prisma-capacity.eu> */
public interface Subscription extends AutoCloseable {

  /**
   * blocks until Catchup or Cancelled event received
   *
   * @return this for fluency
   * @throws SubscriptionCancelledException if Subscription was cancelled before or during the wait
   */
  Subscription awaitCatchup() throws SubscriptionCancelledException;

  /**
   * blocks until Catchup or Cancelled event received
   *
   * @throws TimeoutException if no relevant event was received in time
   */
  Subscription awaitCatchup(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException;

  /** blocks until Complete or Cancelled event received */
  Subscription awaitComplete() throws SubscriptionCancelledException;

  /**
   * blocks until Complete or Cancelled event received
   *
   * @return this
   * @throws TimeoutException if no relevant event was received in time
   */
  Subscription awaitComplete(long waitTimeInMillis)
      throws SubscriptionCancelledException, TimeoutException;
}
