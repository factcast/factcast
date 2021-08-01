package org.factcast.server.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.sql.SQLException;
import lombok.val;
import org.factcast.server.grpc.ServerExceptionLogger.Level;
import org.factcast.test.Slf4jHelper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import slf4jtest.LogLevel;
import slf4jtest.TestLogger;

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
      val uut = spy(underTest);
      val e = new Exception();

      doReturn(Level.INFO).when(uut).resolveLevelFor(e);
      doNothing().when(uut).info(any(), any());

      uut.log(e, "foo");

      verify(uut).resolveLevelFor(e);
      verify(uut).info(e, "foo");
    }

    @Test
    void testLog_warn() {
      val uut = spy(underTest);
      val e = new Exception();

      doReturn(Level.WARN).when(uut).resolveLevelFor(e);
      doNothing().when(uut).warn(any(), any());

      uut.log(e, "foo");

      verify(uut).resolveLevelFor(e);
      verify(uut).warn(e, "foo");
    }

    @Test
    void testLog_error() {
      val uut = spy(underTest);
      val e = new Exception();

      doReturn(Level.ERROR).when(uut).resolveLevelFor(e);
      doNothing().when(uut).error(any(), any());

      uut.log(e, "foo");

      verify(uut).resolveLevelFor(e);
      verify(uut).error(e, "foo");
    }

    @Test
    void doesNotLogForJustSendToCLient() {
      val uut = spy(underTest);
      val e = new Exception();

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
    TestLogger logger = Slf4jHelper.replaceLogger(underTest);
    RuntimeException e = new RuntimeException();
    String id = "foo";
    underTest.error(e, id);

    assertThat(logger.lines().size()).isEqualTo(1);
    assertThat(
            logger.lines().stream()
                .anyMatch(
                    l ->
                        l.level == LogLevel.ErrorLevel
                            && l.text.startsWith(id + " onError – sending Error notification")))
        .isTrue();
  }

  @Test
  void testWarn() {
    TestLogger logger = Slf4jHelper.replaceLogger(underTest);
    RuntimeException e = new RuntimeException();
    String id = "foo";
    underTest.warn(e, id);

    assertThat(logger.lines().size()).isEqualTo(1);
    assertThat(
            logger.lines().stream()
                .anyMatch(
                    l ->
                        l.level == LogLevel.WarnLevel
                            && l.text.startsWith(id + " onError – sending Error notification")))
        .isTrue();
  }

  @Test
  void testInfo() {
    TestLogger logger = Slf4jHelper.replaceLogger(underTest);
    RuntimeException e = new RuntimeException();
    String id = "foo";
    underTest.info(e, id);

    assertThat(logger.lines().size()).isEqualTo(1);
    assertThat(
            logger.lines().stream()
                .anyMatch(
                    l ->
                        l.level == LogLevel.InfoLevel
                            && l.text.startsWith(id + " onError – sending Error notification")))
        .isTrue();
  }
}
