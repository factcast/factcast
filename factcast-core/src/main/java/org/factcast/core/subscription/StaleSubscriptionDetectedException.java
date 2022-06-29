/*
 * Copyright © 2017-2022 factcast.org
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

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class StaleSubscriptionDetectedException extends RuntimeException {
  private static final long serialVersionUID = 5303452267477397256L;

  public StaleSubscriptionDetectedException(long last, long gracePeriod) {
    super(createMessage(last, gracePeriod));
  }

  private static String createMessage(long last, long gracePeriod) {
    if (last == 0L) {
      return "Even though expected due to requesting keepalive, the client did not receive any"
          + " notification at all (waited for "
          + gracePeriod
          + "ms)";
    } else {
      return "Even though expected due to requesting keepalive, the client did not receive any"
          + " notification for the last "
          + gracePeriod
          + "ms. (Last notification was received "
          + last
          + "ms ago)";
    }
  }
}
