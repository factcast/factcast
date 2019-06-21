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
package org.factcast.spring.boot.autoconfigure.server.grpc;

import org.factcast.server.grpc.*;
import org.factcast.server.grpc.codec.*;
import org.factcast.spring.boot.autoconfigure.store.inmem.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.*;

import lombok.*;
import net.jpountz.lz4.*;

@Generated
@Configuration
@ConditionalOnClass({ FactStoreGrpcService.class, LZ4Compressor.class, Lz4GrpcServerCodec.class })
@AutoConfigureAfter(InMemFactStoreAutoConfiguration.class)
public class LZ4ServerAutoConfiguration {
    @Bean
    public Lz4GrpcServerCodec lz4ServerCodec() {
        return new Lz4GrpcServerCodec();
    }
}
