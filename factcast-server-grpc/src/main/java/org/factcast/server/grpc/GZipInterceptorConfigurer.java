/**
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

import org.springframework.stereotype.Component;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import net.devh.springboot.autoconfigure.grpc.server.GlobalServerInterceptorConfigurerAdapter;
import net.devh.springboot.autoconfigure.grpc.server.GlobalServerInterceptorRegistry;

/**
 * globally actives gzip-compression for GRPC responses sent to clients
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Component
public class GZipInterceptorConfigurer extends GlobalServerInterceptorConfigurerAdapter {
    public void addServerInterceptors(GlobalServerInterceptorRegistry registry) {
        registry.addServerInterceptors(new GZipInterceptor());
    }

    static class GZipInterceptor implements ServerInterceptor {

        @Override
        public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            call.setCompression("gzip");
            return next.startCall(call, headers);
        }

    }
}
