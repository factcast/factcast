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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.util.function.Supplier;
import org.factcast.core.FactValidationException;
import org.factcast.server.grpc.GrpcServerExceptionInterceptor.ExceptionHandlingServerCallListener;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;

@ExtendWith(MockitoExtension.class)
class GrpcServerExceptionInterceptorTest {
  @Mock private GrpcRequestMetadata grpcRequestMetadata;
  @Mock private Supplier<GrpcRequestMetadata> grpcMetadataSupplier;
  @InjectMocks private GrpcServerExceptionInterceptor underTest;

  @Nested
  class WhenInterceptingCall<Req, Res> {
    @Mock private ServerCall<Req, Res> serverCall;
    @Mock private ServerCallHandler<Req, Res> serverCallHandler;

    @BeforeEach
    void setup() {}

    @Test
    void wraps() {
      assertThat(underTest.interceptCall(serverCall, new Metadata(), serverCallHandler))
          .isInstanceOf(ExceptionHandlingServerCallListener.class);
    }
  }

  @Nested
  class ExceptionHandlingServerCallListenerTests<Req, Res> {
    @Mock ServerCall.Listener<Req> listener;
    @Mock ServerCall<Req, Res> serverCall;
    @Mock Metadata metadata;
    @Mock GrpcRequestMetadata grpcRequestMetadata;
    @Mock Supplier<GrpcRequestMetadata> grpcMetadataSupplier;

    @BeforeEach
    void setUp() {
      lenient().when(grpcMetadataSupplier.get()).thenReturn(grpcRequestMetadata);
    }

    @InjectMocks ExceptionHandlingServerCallListener<Req, Res> underTest;
    final ArrayIndexOutOfBoundsException ex = new ArrayIndexOutOfBoundsException("ignore me");

    @Nested
    class onMessage {

      @BeforeEach
      void setup() {}

      @Test
      void handlesException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onMessage(any());

        Req msg = null;

        uut.onMessage(msg);
        verify(uut).logIfNecessary(any(), same(ex));
        verify(serverCall).close(eq(Status.UNKNOWN), any(Metadata.class));
        verify(uut).handleException(same(ex), any(), any());
      }

      @Test
      void handlesException_UnsupportedOperationException() {
        final var unsupportedOperationException = new UnsupportedOperationException();

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(unsupportedOperationException).when(listener).onMessage(any());

        Req msg = null;

        uut.onMessage(msg);
        verify(uut).logIfNecessary(any(), same(unsupportedOperationException));
        verify(serverCall).close(eq(Status.UNIMPLEMENTED), any(Metadata.class));
        verify(uut).handleException(same(unsupportedOperationException), any(), any());
      }

      @Test
      void happyPath() {
        Assertions.assertDoesNotThrow(() -> underTest.onMessage(null));
      }
    }

    @Nested
    class onReady {

      @Test
      void handlesException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onReady();

        uut.onReady();

        verify(uut).handleException(same(ex), any(), any());
        verify(uut).logIfNecessary(any(), same(ex));
        verify(serverCall).close(eq(Status.UNKNOWN), any(Metadata.class));
      }

      @Test
      void happyPath() {
        Assertions.assertDoesNotThrow(() -> underTest.onReady());
      }
    }

    @Nested
    class onCancel {

      @Test
      void handlesException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onCancel();

        uut.onCancel();

        verify(uut).handleException(same(ex), any(), any());
        verify(serverCall).close(eq(Status.UNKNOWN), any(Metadata.class));
        verify(uut).handleException(same(ex), any(), any());
      }

      @Test
      void happyPath() {
        Assertions.assertDoesNotThrow(() -> underTest.onCancel());
      }
    }

    @Nested
    class onComplete {

      @Test
      void handlesException() {
        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onComplete();

        uut.onComplete();

        verify(uut).handleException(same(ex), any(), any());
        verify(serverCall).close(eq(Status.UNKNOWN), any(Metadata.class));
        verify(uut).handleException(same(ex), any(), any());
      }

      @Test
      void happyPath() {
        Assertions.assertDoesNotThrow(() -> underTest.onComplete());
      }
    }

    @Nested
    class whenHandlingException {

      @Test
      void handlesCancelByClient() {
        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        String msg = "123";
        var exception = new RequestCanceledByClientException(msg);

        uut.handleException(exception, serverCall, metadata);

        var cap = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(cap.capture(), same(metadata));
        assertThat(cap.getValue().getCode()).isEqualTo(Code.CANCELLED);
        assertThat(cap.getValue().getDescription()).isEqualTo(msg);
      }

      @Test
      void closesOnStatusRuntimeException() {
        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        String msg = "456";
        var md = new Metadata();
        var statusExc = new StatusRuntimeException(Status.ALREADY_EXISTS.withDescription(msg), md);
        uut.handleException(statusExc, serverCall, md);

        var cap = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(cap.capture(), same(md));
        assertThat(cap.getValue().getCode()).isEqualTo(Code.ALREADY_EXISTS);
        assertThat(cap.getValue().getDescription()).isEqualTo(msg);
      }

      @Test
      void closesWithTranslatedException() {
        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        String msg = "456";
        var exception = new FactValidationException(msg);
        var md = new Metadata(); // dont use mock here
        uut.handleException(exception, serverCall, md);

        var cap = ArgumentCaptor.forClass(Metadata.class);
        verify(serverCall).close(any(), cap.capture());
        assertThat(
                cap.getValue()
                    .containsKey(Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER)))
            .isTrue();
        assertThat(
                cap.getValue()
                    .containsKey(Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER)))
            .isTrue();
      }

      @Test
      void translatesCredentialsNotFoundException() {
        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        var exception = new AuthenticationCredentialsNotFoundException("test");

        uut.handleException(exception, serverCall, metadata);

        var cap = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(cap.capture(), any());
        assertThat(cap.getValue().getCode()).isEqualTo(Code.UNAUTHENTICATED);
      }

      @Test
      void translatesAuthenticationException() {
        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        var exception = new BadCredentialsException("test");

        uut.handleException(exception, serverCall, metadata);

        var cap = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(cap.capture(), any());
        assertThat(cap.getValue().getCode()).isEqualTo(Code.PERMISSION_DENIED);
      }
    }
  }
}
