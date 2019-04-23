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
package org.factcast.spring.boot.autoconfigure.client.grpc;

import java.util.*;

import org.factcast.client.grpc.*;
import org.factcast.core.store.*;
import org.factcast.spring.boot.autoconfigure.store.inmem.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

import io.grpc.*;
import net.devh.boot.grpc.client.channelfactory.*;

/**
 * Provides a GrpcFactStore as a FactStore implementation.
 *
 * @author uwe.schaefer@mercateo.com
 */

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Configuration
@ConditionalOnClass({ GrpcFactStore.class, GrpcChannelFactory.class })
@AutoConfigureAfter(InMemFactStoreAutoConfiguration.class)
public class GrpcFactStoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(FactStore.class)
    public FactStore factStore(GrpcChannelFactory af,
            @Value("${grpc.client.factstore.credentials:#{null}}") Optional<String> credentials) {
        org.factcast.client.grpc.FactCastGrpcChannelFactory f = new org.factcast.client.grpc.FactCastGrpcChannelFactory() {

            @Override
            public Channel createChannel(String name, List<ClientInterceptor> interceptors) {
                return af.createChannel(name, interceptors);
            }

            @Override
            public Channel createChannel(String name) {
                return af.createChannel(name);
            }

            @Override
            public void close() {
                af.close();
            }
        };
        return new GrpcFactStore(f, credentials);
    }
}
