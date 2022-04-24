package org.factcast.test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SLF4JTestSpy implements AutoCloseable {
  private final List<ILoggingEvent> loggingEvents = Collections.synchronizedList(new ArrayList<>());
  private final SpyAppender spyAppender;

  public boolean contains(String stringContainedInMessage) {
    return count(stringContainedInMessage) > 0;
  }

  public boolean contains(Level level, String stringContainedInMessage) {
    return count(level, stringContainedInMessage) > 0;
  }

  private long count(Level level, String stringContainedInMessage) {
    return stream()
        .filter(
            e ->
                level.equals(e.getLevel())
                    && e.getFormattedMessage().contains(stringContainedInMessage))
        .count();
  }

  private long count(String stringContainedInMessage) {
    return stream().filter(e -> e.getFormattedMessage().contains(stringContainedInMessage)).count();
  }

  public static SLF4JTestSpy attach() {
    return new SLF4JTestSpy();
  }

  public Stream<ILoggingEvent> stream() {
    return loggingEvents.stream();
  }

  public List<ILoggingEvent> lines() {
    return stream().collect(Collectors.toList());
  }

  private class SpyAppender extends AppenderBase<ILoggingEvent> {
    @Override
    protected void append(ILoggingEvent eventObject) {
      loggingEvents.add(eventObject);
    }
  }

  private SLF4JTestSpy() {
    spyAppender = new SpyAppender();
    spyAppender.start();
    attachSpy(spyAppender);
  }

  @Override
  public void close() throws Exception {
    detachSpy(spyAppender);
    spyAppender.stop();
  }

  private static void detachSpy(SpyAppender spyAppender) {
    ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
        .detachAppender(spyAppender);
  }

  private static void attachSpy(SpyAppender spyAppender) {
    ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME))
        .addAppender(spyAppender);
  }
}
