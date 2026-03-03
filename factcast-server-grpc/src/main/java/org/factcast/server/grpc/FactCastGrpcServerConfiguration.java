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

import io.grpc.CompressorRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import lombok.NonNull;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.observer.HighWaterMarkFetcher;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.server.grpc.metrics.ServerMetrics;
import org.factcast.server.grpc.metrics.ServerMetricsImpl;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.grpc.server.GlobalServerInterceptor;

@Configuration
@EnableConfigurationProperties(GrpcLimitProperties.class)
public class FactCastGrpcServerConfiguration {

  @Bean
  public FactStoreGrpcService factStoreGrpcService(
      FactStore store,
      Supplier<GrpcRequestMetadata> grpcMetaDataProvider,
      GrpcLimitProperties props,
      HighWaterMarkFetcher target,
      ServerMetrics metrics,
      CompressionCodecs compressionCodecs) {
    return new FactStoreGrpcService(
        store, grpcMetaDataProvider, props, target, metrics, compressionCodecs);
  }

  @Bean
  @GlobalServerInterceptor
  public GrpcCompressionInterceptor grpcCompressionInterceptor(
      CompressionCodecs compressionCodecs) {
    return new GrpcCompressionInterceptor(compressionCodecs);
  }

  @Bean
  @GlobalServerInterceptor
  public GrpcServerExceptionInterceptor grpcExceptionInterceptor(Supplier<GrpcRequestMetadata> m) {
    return new GrpcServerExceptionInterceptor(m);
  }

  @Bean
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @GlobalServerInterceptor
  public GrpcRequestMetadataInterceptor grpcRequestMetadataInterceptor() {
    return new GrpcRequestMetadataInterceptor();
  }

  @Bean
  public Supplier<GrpcRequestMetadata> grpcRequestMetadataObjectProvider() {
    return GrpcRequestMetadataInterceptor.METADATA_CTX_KEY::get;
  }

  @Bean
  public ServerMetrics serverMetrics(@NonNull MeterRegistry reg) {
    return new ServerMetricsImpl(reg);
  }

  @Bean
  public CompressionCodecs compressionCodecs(CompressorRegistry compressorRegistry) {
    return new CompressionCodecs(compressorRegistry);
  }
}
