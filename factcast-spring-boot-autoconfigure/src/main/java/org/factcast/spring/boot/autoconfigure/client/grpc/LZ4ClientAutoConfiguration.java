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
package org.factcast.spring.boot.autoconfigure.client.grpc;

import net.jpountz.lz4.LZ4Compressor;
import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.grpc.lz4.Lz4GrpcCodec;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.context.annotation.Bean;
import org.springframework.grpc.client.GrpcChannelFactory;

/**
 * Configures optional LZ4 Codec
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@AutoConfiguration
@ConditionalOnClass({
  LZ4Compressor.class,
  Lz4GrpcCodec.class,
  GrpcFactStore.class,
  GrpcChannelFactory.class
})
@AutoConfigureBefore(GrpcFactStoreAutoConfiguration.class)
public class LZ4ClientAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public Lz4GrpcCodec lz4Codec() {
    return new Lz4GrpcCodec();
  }
}
