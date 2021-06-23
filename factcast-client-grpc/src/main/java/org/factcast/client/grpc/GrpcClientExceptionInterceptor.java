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
package org.factcast.client.grpc;

import io.grpc.*;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.interceptor.GrpcGlobalClientInterceptor;

@Slf4j
@GrpcGlobalClientInterceptor
public class GrpcClientExceptionInterceptor implements ClientInterceptor {

  @Override
  public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
      MethodDescriptor<ReqT, RespT> methodDescriptor, CallOptions callOptions, Channel channel) {
    return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
        channel.newCall(methodDescriptor, callOptions)) {
      @Override
      public void start(Listener<RespT> listener, Metadata headers) {
        super.start(listener, headers);
      }

      @Override
      public void sendMessage(ReqT message) {
        try {
          super.sendMessage(message);
        } catch (RuntimeException ex) {
          handleException(ex);
        }
      }

      @Override
      public void request(int numMessages) {
        try {
          super.request(numMessages);
        } catch (RuntimeException ex) {
          handleException(ex);
        }
      }

      @Override
      public void halfClose() {
        try {
          super.halfClose();
        } catch (RuntimeException ex) {
          handleException(ex);
        }
      }

      @Override
      public void cancel(String message, Throwable cause) {
        try {
          super.cancel(message, cause);
        } catch (RuntimeException ex) {
          handleException(ex);
        }
      }

      private void handleException(RuntimeException ex) {
        if (ex instanceof StatusRuntimeException) {
          throw ClientExceptionHelper.from((StatusRuntimeException) ex);
        } else {
          throw ex;
        }
      }
    };
  }
}
