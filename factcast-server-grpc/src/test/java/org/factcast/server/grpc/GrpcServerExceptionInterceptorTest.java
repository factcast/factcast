package org.factcast.server.grpc;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.grpc.*;
import io.grpc.Status.Code;
import lombok.val;
import org.factcast.core.FactValidationException;
import org.factcast.server.grpc.GrpcServerExceptionInterceptor.ExceptionHandlingServerCallListener;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcServerExceptionInterceptorTest {
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

    @InjectMocks ExceptionHandlingServerCallListener<Req, Res> underTest;
    ArrayIndexOutOfBoundsException ex = new ArrayIndexOutOfBoundsException("ignore me");

    @Nested
    class onMessage {

      @BeforeEach
      void setup() {}

      @Test
      void handlesException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onMessage(any());

        Req msg = null;
        assertThatThrownBy(
                () -> {
                  uut.onMessage(msg);
                })
            .isInstanceOf(ArrayIndexOutOfBoundsException.class);

        verify(uut).handleException(same(ex), any(), any());
      }

      @Test
      void happyPath() {
        underTest.onMessage(null);
      }
    }

    @Nested
    class onReady {

      @Test
      void handlesException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onReady();

        Req msg = null;
        assertThatThrownBy(uut::onReady).isInstanceOf(ArrayIndexOutOfBoundsException.class);

        verify(uut).handleException(same(ex), any(), any());
      }

      @Test
      void happyPath() {
        underTest.onReady();
      }
    }

    @Nested
    class onCancel {

      @Test
      void handlesException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onCancel();

        Req msg = null;
        assertThatThrownBy(uut::onCancel).isInstanceOf(ArrayIndexOutOfBoundsException.class);

        verify(uut).handleException(same(ex), any(), any());
      }

      @Test
      void happyPath() {
        underTest.onCancel();
      }
    }

    @Nested
    class onComplete {

      @Test
      void handlesException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        doThrow(ex).when(listener).onComplete();

        Req msg = null;
        assertThatThrownBy(uut::onComplete).isInstanceOf(ArrayIndexOutOfBoundsException.class);

        verify(uut).handleException(same(ex), any(), any());
      }

      @Test
      void happyPath() {
        underTest.onComplete();
      }
    }

    @Nested
    class whenHandlingException {

      @Test
      void handlesCancelByClient() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);

        String msg = "123";
        val ex = new RequestCanceledByClientException(msg);

        uut.handleException(ex, serverCall, metadata);

        val cap = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(cap.capture(), same(metadata));

        assertThat(cap.getValue().getCode()).isEqualTo(Code.CANCELLED);
        assertThat(cap.getValue().getDescription()).isEqualTo(msg);
      }

      @Test
      void closesOnStatusRuntimeException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        String msg = "456";
        val ex = new StatusRuntimeException(Status.ALREADY_EXISTS.withDescription(msg));
        uut.handleException(ex, serverCall, metadata);

        val cap = ArgumentCaptor.forClass(Status.class);
        verify(serverCall).close(cap.capture(), same(metadata));

        assertThat(cap.getValue().getCode()).isEqualTo(Code.ALREADY_EXISTS);
        assertThat(cap.getValue().getDescription()).isEqualTo(msg);
      }

      @Test
      void closesWithTranslatedException() {

        ExceptionHandlingServerCallListener<Req, Res> uut = spy(underTest);
        String msg = "456";
        val ex = new FactValidationException(msg);
        val metadata = new Metadata(); // dont use mock here
        uut.handleException(ex, serverCall, metadata);

        val cap = ArgumentCaptor.forClass(Metadata.class);
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
    }
  }
}
