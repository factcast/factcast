package org.factcast.core.util;

import java.util.List;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
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
