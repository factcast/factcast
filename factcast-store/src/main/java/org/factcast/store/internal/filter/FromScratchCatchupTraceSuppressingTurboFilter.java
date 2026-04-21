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
package org.factcast.store.internal.filter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import lombok.NonNull;
import org.slf4j.MDC;
import org.slf4j.Marker;

/**
 * Suppresses log events below a configured level on threads that are performing a "from scratch"
 * catchup. The catchup thread sets an MDC attribute (see {@link #MDC_KEY_FROM_SCRATCH}) before
 * starting and removes it when done. This filter checks for that attribute and denies events below
 * the configured minimum level when present.
 */
public class FromScratchCatchupTraceSuppressingTurboFilter extends TurboFilter {

  public static final String MDC_KEY_FROM_SCRATCH = "factcast.catchup.fromscratch";

  private final Level minLevel;

  public FromScratchCatchupTraceSuppressingTurboFilter(@NonNull Level minLevel) {
    this.minLevel = minLevel;
  }

  @Override
  public FilterReply decide(
      Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
    if (MDC.get(MDC_KEY_FROM_SCRATCH) != null && level.toInt() < minLevel.toInt()) {
      return FilterReply.DENY;
    }
    return FilterReply.NEUTRAL;
  }
}
