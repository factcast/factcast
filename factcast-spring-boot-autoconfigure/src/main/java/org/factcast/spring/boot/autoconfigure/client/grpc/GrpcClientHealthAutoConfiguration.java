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
package org.factcast.spring.boot.autoconfigure.client.grpc;

import static org.factcast.client.grpc.FactCastGrpcClientProperties.FACTCAST_GRPC_CLIENT_PREFIX;

import org.factcast.client.grpc.GrpcFactStore;
import org.factcast.client.grpc.GrpcHealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@AutoConfigureAfter(GrpcFactStoreAutoConfiguration.class)
@ConditionalOnClass(GrpcFactStore.class)
@ConditionalOnProperty(
    prefix = FACTCAST_GRPC_CLIENT_PREFIX,
    name = "health",
    havingValue = "true",
    matchIfMissing = true)
public class GrpcClientHealthAutoConfiguration {
  @Bean
  public HealthIndicator grpcChannelHealthIndicator(GrpcHealthIndicator grpcHealthContributor) {
    return () -> {
      if (grpcHealthContributor.isHealthy()) {
        return Health.up().build();
      } else {
        return Health.outOfService().build();
      }
    };
  }
}
