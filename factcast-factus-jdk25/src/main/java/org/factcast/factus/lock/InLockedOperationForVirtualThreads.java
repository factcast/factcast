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
package org.factcast.factus.lock;

/**
 * This class is not so much for actual locking of concurrent access to factcast, but rather a
 * safeguard that makes sure that when inside of a ec.lock().attempt(), you publish to the
 * transaction and not to factus directly (which would be a mistake).
 */
public class InLockedOperationForVirtualThreads implements InLockedOperation {

  private static final ScopedValue<Boolean> isInLockedOperation = ScopedValue.newInstance();

  public void runLocked(Runnable runnable) {
    ScopedValue.where(isInLockedOperation, Boolean.TRUE).run(runnable);
  }

  public void assertNotInLockedOperation() throws IllegalStateException {
    if (isInLockedOperation.orElse(Boolean.FALSE)) {
      throw new IllegalStateException(
          "Currently within locked operation 'Locked.attempt(...)', hence this "
              + "operation is not allowed");
    }
  }
}
