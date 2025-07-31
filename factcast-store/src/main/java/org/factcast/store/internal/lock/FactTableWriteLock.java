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
package org.factcast.store.internal.lock;

public interface FactTableWriteLock {

  int STAR_NAMESPACE_CODE = 5_000;

  void aquireExclusiveTXLock();

  void aquireExclusiveTXLock(int code);

  void aquireSharedTXLock(int code);

  /**
   * Needs to be called every time we publish, to make sure we can serialise conditional publish
   * with lock on namespace *.
   *
   * <p><b>Caution: Needs to be called before acquiring any other lock!</b>
   */
  default void aquireGeneralPublishLock() {
    aquireSharedTXLock(STAR_NAMESPACE_CODE);
  }

  /**
   * Needs to be called every time we publish with a conditional lock on the "*" namespace, as in
   * that case, we need an exclusive lock.
   *
   * <p><b>Caution: Needs to be called before acquiring any other lock!</b>
   */
  default void aquireExclusiveGeneralPublishLock() {
    aquireExclusiveTXLock(STAR_NAMESPACE_CODE);
  }
}
