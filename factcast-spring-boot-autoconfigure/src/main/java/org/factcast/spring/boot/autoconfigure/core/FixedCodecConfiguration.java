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
package org.factcast.spring.boot.autoconfigure.core;

import io.grpc.Compressor;
import io.grpc.CompressorRegistry;
import io.grpc.Decompressor;
import io.grpc.DecompressorRegistry;
import io.grpc.ManagedChannelBuilder;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FixedCodecConfiguration {
  @Bean
  @Primary
  CompressorRegistry fixedCompressorRegistry(ObjectProvider<Compressor> compressors) {
    if (compressors.stream().count() == 0) {
      return CompressorRegistry.getDefaultInstance();
    }
    CompressorRegistry registry = CompressorRegistry.newEmptyInstance();
    compressors.orderedStream().forEachOrdered(registry::register);
    return registry;
  }

  /**
   * The decompressor registry that is set on the {@link
   * ManagedChannelBuilder#decompressorRegistry(DecompressorRegistry) channel builder}.
   *
   * @param decompressors the decompressors to use on the registry
   * @return a new {@link DecompressorRegistry#emptyInstance() registry} with the specified
   *     decompressors or the {@link DecompressorRegistry#getDefaultInstance() default registry} if
   *     no custom decompressors are available in the application context.
   */
  @Bean
  @Primary
  DecompressorRegistry fixedDecompressorRegistry(ObjectProvider<Decompressor> decompressors) {
    if (decompressors.stream().count() == 0) {
      return DecompressorRegistry.getDefaultInstance();
    }
    DecompressorRegistry registry = DecompressorRegistry.emptyInstance();
    for (Decompressor decompressor : decompressors.orderedStream().collect(Collectors.toList())) {
      registry = registry.with(decompressor, false);
    }
    return registry;
  }
}
