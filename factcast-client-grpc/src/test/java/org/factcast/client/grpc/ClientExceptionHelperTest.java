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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientExceptionHelperTest {

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
      e = new FactValidationException("disappointed");
      Metadata metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());

      StatusRuntimeException ex = new StatusRuntimeException(Status.UNKNOWN, metadata);
      assertThat(ClientExceptionHelper.from(ex))
          .isInstanceOf(FactValidationException.class)
          .extracting(Throwable::getMessage)
          .isEqualTo("disappointed");
    }

    @Test
    void extractsCredentialsNotFoundException() {
      Metadata metadata = new Metadata();

      StatusRuntimeException ex = new StatusRuntimeException(Status.UNAUTHENTICATED, metadata);
      assertThat(ClientExceptionHelper.from(ex)).isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void extractsAuthenticationException() {
      Metadata metadata = new Metadata();

      StatusRuntimeException ex = new StatusRuntimeException(Status.PERMISSION_DENIED, metadata);
      assertThat(ClientExceptionHelper.from(ex)).isInstanceOf(StatusRuntimeException.class);
    }

    @ParameterizedTest
    @EnumSource(
        value = Status.Code.class,
        names = {"UNKNOWN", "UNAVAILABLE", "ABORTED", "DEADLINE_EXCEEDED"})
    void wrapsRetryable(Status.Code code) {
      StatusRuntimeException ex = new StatusRuntimeException(code.toStatus());
      assertThat(ClientExceptionHelper.from(ex))
          .isInstanceOf(RetryableException.class)
          .extracting(Throwable::getCause)
          .isSameAs(ex);
    }

    @Test
    void wrapsRetryableCancelledWithMessage() {
      StatusRuntimeException ex =
          new StatusRuntimeException(
              Status.Code.CANCELLED
                  .toStatus()
                  .withDescription(
                      "CANCELLED: RST_STREAM closed stream. HTTP/2 error code: CANCEL"));
      assertThat(ClientExceptionHelper.from(ex))
          .isInstanceOf(RetryableException.class)
          .extracting(Throwable::getCause)
          .isSameAs(ex);
    }

    @Test
    void ignoresCancelledWithNonMatchingMessage() {
      StatusRuntimeException ex =
          new StatusRuntimeException(Status.Code.CANCELLED.toStatus().withDescription("any"));
      assertThat(ClientExceptionHelper.from(ex)).isInstanceOf(StatusRuntimeException.class);
    }

    @Test
    void ignoresNonReconstructableException() {
      e = new MissesRequiredContructorException(1);
      Metadata metadata = new Metadata();
      metadata.put(
          Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
      metadata.put(
          Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
          e.getClass().getName().getBytes());
      StatusRuntimeException ex = new StatusRuntimeException(Status.UNKNOWN, metadata);

      assertThat(ClientExceptionHelper.from(ex))
          .isInstanceOf(RetryableException.class)
          .extracting(Throwable::getCause)
          .isInstanceOf(StatusRuntimeException.class);
    }
  }
}
