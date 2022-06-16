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

import java.io.IOException;
import java.sql.SQLException;
import nl.altindag.log.LogCaptor;
import org.factcast.server.grpc.ServerExceptionLogger.Level;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import slf4jtest.LogLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ServerExceptionLoggerTest {

  @InjectMocks private ServerExceptionLogger underTest;

  @Nested
  class WhenLoging {
    @Mock private Throwable e;

    @BeforeEach
    void setup() {}

    @Test
    void warnsForCertainExceptionWeWantInTheLog() {
      assertThat(underTest.resolveLevelFor(new SQLException(""))).isEqualTo(Level.WARN);
      assertThat(underTest.resolveLevelFor(new IllegalStateException(""))).isEqualTo(Level.WARN);
      assertThat(underTest.resolveLevelFor(new IllegalArgumentException(""))).isEqualTo(Level.WARN);
      assertThat(underTest.resolveLevelFor(new UnsupportedOperationException("")))
          .isEqualTo(Level.WARN);

      assertThat(underTest.resolveLevelFor(new RuntimeException(new SQLException(""))))
          .isEqualTo(Level.WARN);
      assertThat(
              underTest.resolveLevelFor(
                  new RuntimeException(new RuntimeException(new SQLException("")))))
          .isEqualTo(Level.WARN);

      // is assignable from IllegalArgException
      assertThat(underTest.resolveLevelFor(new NumberFormatException(""))).isEqualTo(Level.WARN);
    }

    @Test
    void logsInfoLevelForUnknownExceptions() {
      assertThat(underTest.resolveLevelFor(new RuntimeException(""))).isEqualTo(Level.INFO);
    }

    @Test
    void skippsLoggingIOException() {
      assertThat(underTest.resolveLevelFor(new IOException("")))
          .isEqualTo(Level.JUST_SEND_TO_CONSUMER);
    }

    @Test
    void testLog_info() {
      var uut = spy(underTest);
      var e = new Exception();

      doReturn(Level.INFO).when(uut).resolveLevelFor(e);
      doNothing().when(uut).info(any(), any());

      uut.log(e, "foo");

      verify(uut).resolveLevelFor(e);
      verify(uut).info(e, "foo");
    }

    @Test
    void testLog_warn() {
      var uut = spy(underTest);
      var e = new Exception();

      doReturn(Level.WARN).when(uut).resolveLevelFor(e);
      doNothing().when(uut).warn(any(), any());

      uut.log(e, "foo");

      verify(uut).resolveLevelFor(e);
      verify(uut).warn(e, "foo");
    }

    @Test
    void testLog_error() {
      var uut = spy(underTest);
      var e = new Exception();

      doReturn(Level.ERROR).when(uut).resolveLevelFor(e);
      doNothing().when(uut).error(any(), any());

      uut.log(e, "foo");

      verify(uut).resolveLevelFor(e);
      verify(uut).error(e, "foo");
    }

    @Test
    void doesNotLogForJustSendToCLient() {
      var uut = spy(underTest);
      var e = new Exception();

      doReturn(Level.JUST_SEND_TO_CONSUMER).when(uut).resolveLevelFor(e);

      uut.log(e, "foo");

      verify(uut).log(any(), any()); // we just did that
      verify(uut).resolveLevelFor(e);

      // not logging happens
      verifyNoMoreInteractions(uut);
    }
  }

  @Test
  void testError() {
    LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
    RuntimeException e = new RuntimeException();
    String id = "foo";
    underTest.error(e, id);

    assertThat(logCaptor.getLogs()).hasSize(2);
    assertThat(logCaptor.getLogEvents().stream())
        .anyMatch(
            l ->
                l.getLevel() == LogLevel.ErrorLevel.toString()
                    && l.getFormattedMessage()
                        .startsWith(id + " onError – sending Error notification"));
  }

  @Test
  void testWarn() {
    LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
    RuntimeException e = new RuntimeException();
    String id = "foo";
    underTest.warn(e, id);

    assertThat(logCaptor.getLogs()).hasSize(2);
    assertThat(logCaptor.getLogEvents().stream())
        .anyMatch(
            l ->
                l.getLevel() == LogLevel.WarnLevel.toString()
                    && l.getFormattedMessage()
                        .startsWith(id + " onError – sending Error notification"));
  }

  @Test
  void testInfo() {
    LogCaptor logCaptor = LogCaptor.forClass(underTest.getClass());
    RuntimeException e = new RuntimeException();
    String id = "foo";
    underTest.info(e, id);

    assertThat(logCaptor.getLogs()).hasSize(2);
    assertThat(logCaptor.getLogEvents().stream())
        .anyMatch(
            l ->
                l.getLevel() == LogLevel.InfoLevel.toString()
                    && l.getFormattedMessage()
                        .startsWith(id + " onError – sending Error notification"));
  }
}
