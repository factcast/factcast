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

@Deprecated
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
