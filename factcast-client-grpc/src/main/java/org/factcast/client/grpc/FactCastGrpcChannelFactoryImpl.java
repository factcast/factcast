/*
 * Copyright © 2017-2024 factcast.org
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

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import lombok.NonNull;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

public class FactCastGrpcChannelFactoryImpl implements FactCastGrpcChannelFactory {

  private final Set<Supplier<Boolean>> healthSuppliers;
  private final GrpcChannelFactory cf;

  public FactCastGrpcChannelFactoryImpl(@NonNull GrpcChannelFactory cf) {
    this.cf = cf;
    this.healthSuppliers = new HashSet<>();
  }

  @Override
  public Channel createChannel(
      @NonNull String name, @NonNull List<ClientInterceptor> interceptors) {
    final ManagedChannel channel =
        cf.createChannel(name, ChannelBuilderOptions.defaults().withInterceptors(interceptors));

    storeConnectivityStateSupplier(channel);

    return channel;
  }

  @Override
  public Channel createChannel(@NonNull String name) {
    final ManagedChannel channel = cf.createChannel(name);

    storeConnectivityStateSupplier(channel);

    return channel;
  }

  private void storeConnectivityStateSupplier(ManagedChannel channel) {
    healthSuppliers.add(() -> channel.getState(false) != ConnectivityState.TRANSIENT_FAILURE);
  }

  @Override
  public boolean isHealthy() {
    return healthSuppliers.stream().allMatch(Supplier::get);
  }
}
