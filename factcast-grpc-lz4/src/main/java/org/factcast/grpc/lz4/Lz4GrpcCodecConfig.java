/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.grpc.lz4;

import io.grpc.Codec;
import io.grpc.CompressorRegistry;
import io.grpc.DecompressorRegistry;
import javax.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// TODO autoconfig setup
@Configuration
public class Lz4GrpcCodecConfig {

  private final CompressorRegistry compressorRegistry;
  private final DecompressorRegistry decompressorRegistry;
  private final Codec lz4GrpcCodec;

  public Lz4GrpcCodecConfig(
      CompressorRegistry compressorRegistry,
      DecompressorRegistry decompressorRegistry,
      Codec lz4GrpcCodec) {
    this.compressorRegistry = compressorRegistry;
    this.decompressorRegistry = decompressorRegistry;
    this.lz4GrpcCodec = lz4GrpcCodec;
  }

  @Bean
  public Codec lz4GrpcCodec() {
    return new Lz4GrpcCodec();
  }

  @PostConstruct
  public void registerCodec() {
    compressorRegistry.register(lz4GrpcCodec);
    decompressorRegistry.with(lz4GrpcCodec, true);
  }
}
