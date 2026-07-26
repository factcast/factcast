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
package org.factcast.store;

import ch.qos.logback.classic.Level;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@Accessors(fluent = false)
public class LogSuppressionProperties {

  boolean enabled = false;

  /**
   * If set to a log level (e.g. "DEBUG", "INFO"), log events below that level are potentially
   * suppressed on threads running code marked for log supression. For example, setting this to
   * "DEBUG" suppresses TRACE logs; "INFO" suppresses both TRACE and DEBUG. Uses MDC + a Logback
   * TurboFilter to selectively suppress only the affected threads. If unset (null), no filtering is
   * applied.
   */
  Level minLogLevel = Level.INFO;

  /**
   * Number of log events (below the configured min level) to allow through before suppression kicks
   * in during a suppressed block. This gives developers initial debugging context while still
   * protecting downstream log aggregators from being overwhelmed. Defaults to 1000. Only effective
   * when {@link #enabled}.
   */
  @Min(0)
  int threshold = 1000;

  /**
   * After the threshold is exceeded, allow 1 out of every N suppressed log events through instead
   * of suppressing all. 0 disables sampling (full suppression after threshold). Defaults to 1000.
   * Only effective when {@link #enabled}.
   */
  @Min(0)
  int sampleRate = 1000;
}
