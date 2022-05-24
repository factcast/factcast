/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.server.grpc;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
// categorize and either skip, or log to a matching loglevel. Not the nicest code to write :D
public class ServerExceptionLogger {

  public static final String EXCEPTION_MESSAGE = "{} onError – sending Error notification {}";

  private static final List<Class<? extends Throwable>> WARNING_LEVEL_EXCEPTIONS =
      Lists.newArrayList(
          IllegalStateException.class,
          IllegalArgumentException.class,
          UnsupportedOperationException.class,
          SQLException.class);

  private static final List<Class<? extends Throwable>> SKIPPED_EXCEPTIONS =
      Lists.newArrayList(IOException.class);

  enum Level {
    ERROR,
    WARN,
    INFO,
    JUST_SEND_TO_CONSUMER
  }

  public void log(Throwable e, @NonNull String id) {
    switch (resolveLevelFor(e)) {
      case INFO:
        info(e, id);
        break;
      case WARN:
        warn(e, id);
        break;
      case ERROR:
        error(e, id);
        break;
      case JUST_SEND_TO_CONSUMER:
        // TODO remove again
        info(e, id);
        // do not log
        break;
      default:
        log.error("Unhandled case for log level from {} - falling back to ERROR", e.getClass());
        error(e, id);
        break;
    }
  }

  @NonNull
  @VisibleForTesting
  Level resolveLevelFor(Throwable e) {

    if (SKIPPED_EXCEPTIONS.stream().anyMatch(c -> c.isAssignableFrom(e.getClass()))) {
      return Level.JUST_SEND_TO_CONSUMER;
    }
    if (WARNING_LEVEL_EXCEPTIONS.stream().anyMatch(c -> c.isAssignableFrom(e.getClass()))) {
      return Level.WARN;
    }
    // extend over time

    // fall through
    Throwable cause = e.getCause();
    if (cause == null) {
      return Level.INFO;
    } else {
      return resolveLevelFor(cause);
    }
  }

  @VisibleForTesting
  void info(Throwable e, String id) {
    log.info(EXCEPTION_MESSAGE, id, e.getMessage());
  }

  @VisibleForTesting
  void warn(Throwable e, String id) {
    log.warn(EXCEPTION_MESSAGE, id, e.getMessage());
  }

  @VisibleForTesting
  void error(Throwable e, String id) {
    log.error(EXCEPTION_MESSAGE, id, e.getMessage());
  }
}
