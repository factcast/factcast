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

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import lombok.NonNull;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelConfigurer;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.factcast.client.grpc.FactCastGrpcClientProperties;
import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.core.store.FactStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Provides a GrpcFactStore as a FactStore implementation.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Configuration
@ConditionalOnClass({GrpcFactStore.class, GrpcChannelFactory.class})
@Import(FactCastGrpcClientProperties.class)
@EnableConfigurationProperties
public class GrpcFactStoreAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(FactStore.class)
  public FactStore factStore(
      @NonNull GrpcChannelFactory af,
      // we need a new namespace for those client properties
      @NonNull @Value("${grpc.client.factstore.credentials:#{null}}") Optional<String> credentials,
      @NonNull FactCastGrpcClientProperties properties,
      @Nullable @Value("${spring.application.name:#{null}}") String applicationName) {
    org.factcast.client.grpc.FactCastGrpcChannelFactory f =
        new org.factcast.client.grpc.FactCastGrpcChannelFactory() {

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

    String id = properties.getId();
    if (id == null) {
      // fall back to applicationName if set
      if (applicationName != null && !applicationName.trim().isEmpty()) {
        id = applicationName;
      }
    }

    return new GrpcFactStore(f, credentials, properties, id);
  }

  @Bean
  public GrpcChannelConfigurer retryChannelConfigurer() {
    return (channelBuilder, name) -> channelBuilder.enableRetry().maxRetryAttempts(100);
  }
}
