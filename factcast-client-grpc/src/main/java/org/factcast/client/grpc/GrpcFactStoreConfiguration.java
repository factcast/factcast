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
package org.factcast.client.grpc;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

/**
 * Provides a GrpcFactStore as a FactStore implementation.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Import(ClientLZ4Configuration.class)
@Configuration
public class GrpcFactStoreConfiguration {

    @Bean
    public GrpcFactStore grpcFactStore(AddressChannelFactory af) {
        return new GrpcFactStore(af);
    }
}
