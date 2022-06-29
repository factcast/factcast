/*
 * Copyright © 2017-2020 factcast.org
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

import com.google.common.annotations.VisibleForTesting;
import io.grpc.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.factcast.core.FactValidationException;
import org.slf4j.Logger;

@Slf4j
@GrpcGlobalServerInterceptor
@RequiredArgsConstructor
public class GrpcServerExceptionInterceptor implements ServerInterceptor {
  final GrpcRequestMetadata scopedBean;

  @Override
  public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> serverCall,
      Metadata metadata,
      ServerCallHandler<ReqT, RespT> serverCallHandler) {
    ServerCall.Listener<ReqT> listener = serverCallHandler.startCall(serverCall, metadata);
    return new ExceptionHandlingServerCallListener<>(listener, serverCall, metadata, scopedBean);
  }

  static class ExceptionHandlingServerCallListener<ReqT, RespT>
      extends ForwardingServerCallListener.SimpleForwardingServerCallListener<ReqT> {
    private final ServerCall<ReqT, RespT> serverCall;

    private final Metadata metadata;
    private final GrpcRequestMetadata grpcMetadata;

    ExceptionHandlingServerCallListener(
        ServerCall.Listener<ReqT> listener,
        ServerCall<ReqT, RespT> serverCall,
        Metadata metadata,
        @NonNull GrpcRequestMetadata grpcMetadata) {
      super(listener);
      this.serverCall = serverCall;
      this.metadata = metadata;
      this.grpcMetadata = grpcMetadata;
    }

    @Override
    public void onReady() {
      try {
        super.onReady();
      } catch (RuntimeException ex) {
        handleException(ex, serverCall, metadata);
      }
    }

    @Override
    public void onCancel() {
      try {
        super.onCancel();
      } catch (RuntimeException ex) {
        handleException(ex, serverCall, metadata);
      }
    }

    @Override
    public void onComplete() {
      try {
        super.onComplete();
      } catch (RuntimeException ex) {
        handleException(ex, serverCall, metadata);
      }
    }

    @Override
    public void onMessage(ReqT message) {
      try {
        super.onMessage(message);
      } catch (RuntimeException ex) {
        handleException(ex, serverCall, metadata);
      }
    }

    @Override
    public void onHalfClose() {
      try {
        super.onHalfClose();
      } catch (RuntimeException ex) {
        handleException(ex, serverCall, metadata);
      }
    }

    @VisibleForTesting
    void handleException(
        RuntimeException exception, ServerCall<ReqT, RespT> serverCall, Metadata metadata) {

      if (exception instanceof RequestCanceledByClientException) {
        // maybe we can even skip this close call?
        serverCall.close(Status.CANCELLED.withDescription(exception.getMessage()), metadata);
        String clientId = grpcMetadata.clientIdAsString();
        log.debug("Connection cancelled by client '{}'.", clientId);
        return;
      }

      // in case someone knows exactly what status to throw
      if (exception instanceof StatusRuntimeException) {
        var e = (StatusRuntimeException) exception;
        if (!Status.PERMISSION_DENIED.equals(e.getStatus())) {
          log.error("", e);
        }
        serverCall.close(e.getStatus(), metadata);
        return;
      }

      logIfNecessary(log, exception);
      StatusRuntimeException sre = ServerExceptionHelper.translate(exception);
      serverCall.close(sre.getStatus(), sre.getTrailers());
    }

    @VisibleForTesting
    protected void logIfNecessary(@NonNull Logger logger, @NonNull RuntimeException exception) {
      String clientId = grpcMetadata.clientIdAsString();
      if (exception instanceof FactValidationException) {
        logger.warn("Exception triggered by client '{}':", clientId, exception);
      } else {
        logger.error("Exception triggered by client '{}':", clientId, exception);
      }
    }
  }
}
