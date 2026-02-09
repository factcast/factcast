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

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.Headers;

// TODO configure interceptors
@RequiredArgsConstructor
@Slf4j
public class GrpcCompressionInterceptor implements ServerInterceptor {

  private final CompressionCodecs codecs;

  @Override
  public <ReqT, RespT> Listener<ReqT> interceptCall(
      ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
    codecs
        .selectFrom(headers.get(Headers.MESSAGE_COMPRESSION))
        .ifPresent(
            c -> {
              log.trace("setting response compression default to {}", c);
              call.setCompression(c);
              // server code still needs to set call response to compressible.
              // defaults to not use any compression.
              call.setMessageCompression(false);
            });
    return next.startCall(call, headers);
  }
}
