/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.core.util;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.util.List;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StackTraceCallerHelper {
  private static final List<String> INTERNAL_PACKAGE_PREFIXES =
      Lists.newArrayList(
          "org.factcast.",
          "java.",
          "javax.",
          "jdk.",
          "sun.",
          "com.sun.",
          "org.springframework.",
          "com.google.common.");

  @VisibleForTesting
  public static StackTraceElement findCallerFrame(StackTraceElement[] stack) {
    for (StackTraceElement frame : stack) {
      if (isExternalFrame(frame)) {
        return frame;
      }
    }
    // fallback: use original hardcoded index behavior
    return stack.length > 3 ? stack[3] : stack[stack.length - 1];
  }

  private static boolean isExternalFrame(StackTraceElement frame) {
    String className = frame.getClassName();
    for (String prefix : INTERNAL_PACKAGE_PREFIXES) {
      if (className.startsWith(prefix)) {
        return false;
      }
    }
    return true;
  }
}
