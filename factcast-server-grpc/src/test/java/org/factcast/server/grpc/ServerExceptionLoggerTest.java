package org.factcast.server.grpc;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.sql.SQLException;
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
  }
}
