package org.factcast.client.grpc;

import static org.assertj.core.api.Assertions.*;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.io.IOException;
import org.factcast.core.FactValidationException;
import org.factcast.core.store.RetryableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientExceptionHelperTest {

  @InjectMocks private ClientExceptionHelper underTest;

  @Nested
  class WhenFroming {
    @Mock private Throwable e;

    @BeforeEach
    void setup() {}

    @Test
    void wrapsUnrelatedException() {
      IOException e = new IOException();
      assertThat(ClientExceptionHelper.from(e))
          .isInstanceOf(RuntimeException.class)
          .extracting(Throwable::getCause)
          .isSameAs(e);
    }

    @Test
    void extractsTransportedException() {
      var e = new FactValidationException("disappointed");
      var metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());

      var ex = new StatusRuntimeException(Status.UNKNOWN, metadata);
      assertThat(ClientExceptionHelper.from(ex))
          .isInstanceOf(FactValidationException.class)
          .extracting(Throwable::getMessage)
          .isEqualTo("disappointed");
    }

    @Test
    void wrapsRetryable() {
      var ex = new StatusRuntimeException(Status.UNKNOWN);
      assertThat(ClientExceptionHelper.from(ex))
          .isInstanceOf(RetryableException.class)
          .extracting(Throwable::getCause)
          .isSameAs(ex);
    }

    @Test
    void ignoresNonReconstructableException() {
      var e = new MissesRequiredContructorException(1);
      var metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());
      var ex = new StatusRuntimeException(Status.UNKNOWN, metadata);

      assertThat(ClientExceptionHelper.from(ex))
          .isInstanceOf(RetryableException.class)
          .extracting(Throwable::getCause)
          .isInstanceOf(StatusRuntimeException.class);
    }
  }
}
