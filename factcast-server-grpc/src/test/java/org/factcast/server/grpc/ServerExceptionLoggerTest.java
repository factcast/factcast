package org.factcast.server.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.sql.SQLException;
import lombok.val;
import org.factcast.server.grpc.ServerExceptionLogger.Level;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

  @Nested
  class WhenResolving {
    @Test
    void testResolve() {}
  }
}
