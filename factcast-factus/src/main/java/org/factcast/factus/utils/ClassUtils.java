package org.factcast.factus.utils;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class ClassUtils {
  /**
   * @param clazz
   * @return String
   */
  public static String getNameFor(@NonNull Class<?> clazz) {
    Class<?> c = clazz;
    while (c.getName().contains("$$EnhancerBySpring") || c.getName().contains("CGLIB")) {
      c = c.getSuperclass();
    }

    return c.getName();
  }
}
