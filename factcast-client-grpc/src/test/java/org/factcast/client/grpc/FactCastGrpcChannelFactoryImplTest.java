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
package org.factcast.client.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.grpc.client.GrpcChannelFactory;

@ExtendWith(MockitoExtension.class)
class FactCastGrpcChannelFactoryImplTest {

  @Mock private GrpcChannelFactory cf;
  @InjectMocks @Spy private FactCastGrpcChannelFactoryImpl underTest;

  @Nested
  class WhenCreatingChannelWithInterceptors {
    @Mock private ClientInterceptor clientInterceptor;

    @Test
    void returnsChannel() {
      doNothing().when(underTest).storeConnectivityStateSupplier(any());
      String name = "testChannel";

      underTest.createChannel(name, Lists.newArrayList(clientInterceptor));

      verify(cf)
          .createChannel(eq(name), argThat(cb -> cb.interceptors().contains(clientInterceptor)));
      verify(underTest).storeConnectivityStateSupplier(any());
    }
  }

  @Nested
  class WhenCreatingChannel {
    @Test
    void returnsChannel() {
      doNothing().when(underTest).storeConnectivityStateSupplier(any());
      String name = "testChannel";

      underTest.createChannel(name);

      verify(cf).createChannel(name);
      verify(underTest).storeConnectivityStateSupplier(any());
    }
  }

  @Nested
  class HealthChecks {
    @Mock private ManagedChannel channel;

    @Test
    void isHealthy() {
      when(channel.getState(anyBoolean()))
          .thenReturn(
              ConnectivityState.READY,
              ConnectivityState.IDLE,
              ConnectivityState.CONNECTING,
              ConnectivityState.SHUTDOWN);

      underTest.storeConnectivityStateSupplier(channel);
      underTest.storeConnectivityStateSupplier(channel);
      underTest.storeConnectivityStateSupplier(channel);
      underTest.storeConnectivityStateSupplier(channel);

      assertThat(underTest.isHealthy()).isTrue();
    }

    @Test
    void notHealthy() {
      when(channel.getState(anyBoolean()))
          .thenReturn(ConnectivityState.READY, ConnectivityState.TRANSIENT_FAILURE);

      underTest.storeConnectivityStateSupplier(channel);
      underTest.storeConnectivityStateSupplier(channel);

      assertThat(underTest.isHealthy()).isFalse();
    }
  }
}
