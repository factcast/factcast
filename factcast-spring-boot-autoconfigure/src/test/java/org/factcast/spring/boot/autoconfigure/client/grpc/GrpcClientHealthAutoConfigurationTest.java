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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.factcast.client.grpc.GrpcHealthIndicator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class GrpcClientHealthAutoConfigurationTest {

  @InjectMocks private GrpcClientHealthAutoConfiguration underTest;

  @Nested
  class WhenGrpcingChannelHealthIndicator {
    @Mock private GrpcHealthIndicator grpcHealthContributor;

    @Test
    void notHealthy() {
      when(grpcHealthContributor.isHealthy()).thenReturn(false);
      assertThat(underTest.grpcChannelHealthIndicator(grpcHealthContributor).health().getStatus())
          .isEqualTo(Status.OUT_OF_SERVICE);
    }

    @Test
    void healthy() {
      when(grpcHealthContributor.isHealthy()).thenReturn(true);
      assertThat(underTest.grpcChannelHealthIndicator(grpcHealthContributor).health().getStatus())
          .isEqualTo(Status.UP);
    }
  }
}
