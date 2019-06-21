/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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

import org.factcast.grpc.api.*;

import io.grpc.*;
import io.grpc.ServerCall.*;
import lombok.*;
import net.devh.boot.grpc.server.interceptor.*;

@GrpcGlobalServerInterceptor
@RequiredArgsConstructor
public class GrpcCompressionInterceptor implements ServerInterceptor {

    public final Metadata.Key<String> GRPC_ACCEPT_ENCODING = Metadata.Key.of("grpc-accept-encoding",
            Metadata.ASCII_STRING_MARSHALLER);

    private final CompressionCodecs codecs;

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
            Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        codecs.selectFrom(headers.get(GRPC_ACCEPT_ENCODING)).ifPresent(c -> {
            call.setCompression(c);
            // server code still needs to set call response to compressible.
            // defaults to not use any compression.
            call.setMessageCompression(false);
        });
        return next.startCall(call, headers);
    }

}
