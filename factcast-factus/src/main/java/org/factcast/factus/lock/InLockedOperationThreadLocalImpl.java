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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InLockedOperationThreadLocalImpl implements InLockedOperation {

  private static final ThreadLocal<Boolean> isInLockedOperation =
      ThreadLocal.withInitial(() -> false);

  @Override
  public void runLocked(Runnable runnable) {
    isInLockedOperation.set(true);
    try {
      runnable.run();
    } finally {
      isInLockedOperation.set(false);
    }
  }

  public void assertNotInLockedOperation() throws IllegalStateException {
    if (Boolean.TRUE.equals(isInLockedOperation.get())) {
      throw new IllegalStateException(
          "Currently within locked operation 'Locked.attempt(...)', hence this "
              + "operation is not"
              + " allowed");
    }
  }
}
