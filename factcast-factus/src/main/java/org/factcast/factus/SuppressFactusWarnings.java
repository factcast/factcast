package org.factcast.factus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Arrays;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE})
public @interface SuppressFactusWarnings {
  Warning[] warning();

  String note() default "";

  enum Warning {
    ALL,
    PUBLIC_HANDLER_METHOD;

    public boolean isSuppressed(SuppressFactusWarnings annotation) {
      if (annotation == null) return false;
      else return Arrays.stream(annotation.warning()).anyMatch(w -> w == ALL || w == this);
    }
  }
}
