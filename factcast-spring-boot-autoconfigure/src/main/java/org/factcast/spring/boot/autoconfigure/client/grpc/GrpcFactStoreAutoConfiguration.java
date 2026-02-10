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

import io.grpc.CompressorRegistry;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import org.factcast.client.grpc.*;
import org.factcast.core.store.FactStore;
import org.factcast.grpc.api.CompressionCodecs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.grpc.client.GrpcChannelBuilderCustomizer;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.util.StringUtils;

/**
 * Provides a GrpcFactStore as a FactStore implementation.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@AutoConfiguration
@ConditionalOnClass({GrpcFactStore.class, GrpcChannelFactory.class})
@Import(FactCastGrpcClientProperties.class)
@EnableConfigurationProperties
public class GrpcFactStoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(FactStore.class)
  @Lazy
  public FactStore factStore(
      @NonNull GrpcChannelFactory af,
      // we need a new namespace for those client properties
      @NonNull @Value("${grpc.client.factstore.credentials:#{null}}") Optional<String> credentials,
      @NonNull FactCastGrpcClientProperties properties,
      @NonNull CompressionCodecs compressionCodecs,
      @Nullable @Value("${spring.application.name:#{null}}") String applicationName) {

    FactCastGrpcChannelFactory f = FactCastGrpcChannelFactory.createDefault(af);
    String id =
        Optional.ofNullable(properties.getId())
            .orElseGet(
                () ->
                    Optional.ofNullable(applicationName)
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .orElse(null));

    return new GrpcFactStore(f, credentials, properties, compressionCodecs, id);
  }

  @Bean
  public GrpcChannelBuilderCustomizer retryChannelConfigurer() {
    return (name, channelBuilder) -> channelBuilder.enableRetry().maxRetryAttempts(100);
  }

  @Bean
  public CompressionCodecs compressionCodecs(CompressorRegistry compressorRegistry) {
    return new CompressionCodecs(compressorRegistry);
  }
}
