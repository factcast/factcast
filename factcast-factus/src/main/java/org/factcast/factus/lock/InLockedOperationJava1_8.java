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

/**
 * This class is not so much for actual locking of concurrent access to factcast, but rather a
 * safeguard that makes sure that when inside of a ec.lock().attempt(), you publish to the
 * transaction and not to factus directly (which would be a mistake).
 */
@Slf4j
public class InLockedOperationJava1_8 implements InLockedOperation {

  private static final ThreadLocal<Boolean> isInLockedOperation =
      ThreadLocal.withInitial(() -> false);

  @Override
  public void runLocked(Runnable runnable) {
    detectVirtualThread();

    isInLockedOperation.set(true);
    try {
      runnable.run();
    } finally {
      isInLockedOperation.set(false);
    }
  }

  public void assertNotInLockedOperation() throws IllegalStateException {
    if (isInLockedOperation.get()) {
      throw new IllegalStateException(
          "Currently within locked operation 'Locked.attempt(...)', hence this "
              + "operation is not"
              + " allowed");
    }
  }

  private static final boolean IS_VIRTUAL_THREAD_SUPPORTED;
  private static volatile boolean DETECTED_VIRTUAL_THREAD_ONCE;

  static {
    IS_VIRTUAL_THREAD_SUPPORTED = isVirtualThreadSupported();
  }

  public static boolean isVirtualThreadSupported() {
    try {
      Thread.class.getMethod("isVirtual");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  private static void detectVirtualThread() {
    if (!IS_VIRTUAL_THREAD_SUPPORTED) {
      return;
    }

    if (DETECTED_VIRTUAL_THREAD_ONCE || isThisThreadVirtual()) {
      // maybe this thread isn't, but that isn't the point. We ran already on a virtual
      // thread, keep bugging until they fix this.
      DETECTED_VIRTUAL_THREAD_ONCE = true;
      log.warn(
          "Using factus with virtual threads is dangerous! See README in "
              + "'factcast-factus-jdk25' on how to fix this!");
    }
  }

  private static boolean isThisThreadVirtual() {
    try {
      // should become more efficient over time
      Object result = Thread.class.getMethod("isVirtual").invoke(Thread.currentThread());
      return Boolean.TRUE.equals(result);

    } catch (ReflectiveOperationException t) {
      // not expected, we checked before if the isVirtual method exists
      log.warn("Got exception when checking if thread is virtual.", t);
      return false;
    }
  }
}
