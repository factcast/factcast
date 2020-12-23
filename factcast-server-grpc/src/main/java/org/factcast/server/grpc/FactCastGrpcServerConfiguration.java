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

import net.devh.boot.grpc.server.scope.GrpcRequestScope;
import org.factcast.core.store.FactStore;
import org.factcast.grpc.api.CompressionCodecs;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

@Configuration
@EnableConfigurationProperties(GrpcLimitProperties.class)
public class FactCastGrpcServerConfiguration {

  @Bean
  public FactStoreGrpcService factStoreGrpcService(
      FactStore store, GrpcRequestMetadata grpcMetaData, GrpcLimitProperties props) {
    return new FactStoreGrpcService(store, grpcMetaData, props);
  }

  @Bean
  public GrpcCompressionInterceptor grpcCompressionInterceptor() {
    return new GrpcCompressionInterceptor(new CompressionCodecs());
  }

  @Bean
  public GrpcExceptionInterceptor grpcExceptionInterceptor() {
    return new GrpcExceptionInterceptor();
  }

  @Bean
  public GrpcRequestMetadataInterceptor grpcRequestMetadataInterceptor(
      GrpcRequestMetadata grpcMetaData) {
    return new GrpcRequestMetadataInterceptor(grpcMetaData);
  }

  @Bean
  @Scope(
      scopeName = GrpcRequestScope.GRPC_REQUEST_SCOPE_NAME,
      proxyMode = ScopedProxyMode.TARGET_CLASS)
  GrpcRequestMetadata grpcMetaData() {
    return new GrpcRequestMetadata();
  }
}
