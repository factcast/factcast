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

  private void info(Throwable e, String id) {
    log.info(EXCEPTION_MESSAGE, id, e.getMessage());
  }

  private void warn(Throwable e, String id) {
    log.warn(EXCEPTION_MESSAGE, id, e.getMessage());
  }

  private void error(Throwable e, String id) {
    log.warn(EXCEPTION_MESSAGE, id, e.getMessage());
  }
}
