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
package org.factcast.client.grpc.boot1;

import java.util.List;
import java.util.Optional;

import org.factcast.client.grpc.FactCastGrpcChannelFactory;
import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.core.store.FactStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import net.devh.springboot.autoconfigure.grpc.client.GrpcChannelFactory;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Configuration
@ConditionalOnClass(GrpcChannelFactory.class)
public class BackwardsCompatibleConfiguration {

    @Bean
    @ConditionalOnMissingBean(FactStore.class)
    public FactStore factStore(GrpcChannelFactory af,
            @Value("${grpc.client.factstore.credentials:#{null}}") Optional<String> credentials) {
        FactCastGrpcChannelFactory f = new FactCastGrpcChannelFactory() {

            @Override
            public Channel createChannel(String name, List<ClientInterceptor> interceptors) {
                return af.createChannel(name, interceptors);
            }

            @Override
            public Channel createChannel(String name) {
                return af.createChannel(name);
            }

            @Override
            public void close() throws Exception {
                af.close();
            }
        };
        return new GrpcFactStore(f, credentials);
    }
}
