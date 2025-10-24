/*
 * Copyright © 2017-2025 factcast.org
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

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class LockedUtil {
  private static final boolean VIRTUAL_THREADS_SUPPORTED;
  private static final boolean SCOPED_VALUE_AVAILABLE;

  static {
    VIRTUAL_THREADS_SUPPORTED = detectVirtualThreadSupported();
    SCOPED_VALUE_AVAILABLE = detectScopedValueAvailable();
  }

  private static boolean detectScopedValueAvailable() {
    try {
      Class.forName("java.lang.ScopedValue");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private static boolean detectVirtualThreadSupported() {
    try {
      Thread.class.getMethod("isVirtual");
      return true;
    } catch (NoSuchMethodException e) {
      return false;
    }
  }

  public static boolean isVirtualThreadSupported() {
    return VIRTUAL_THREADS_SUPPORTED;
  }

  public static boolean isScopedValueAvailable() {
    return SCOPED_VALUE_AVAILABLE;
  }
}
