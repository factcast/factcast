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
import java.util.List;
import lombok.NonNull;
import org.springframework.grpc.client.ChannelBuilderOptions;
import org.springframework.grpc.client.GrpcChannelFactory;

public class FactCastGrpcChannelFactoryImpl implements FactCastGrpcChannelFactory {

  private final GrpcChannelFactory cf;

  public FactCastGrpcChannelFactoryImpl(@NonNull GrpcChannelFactory cf) {
    this.cf = cf;
  }

  @Override
  public Channel createChannel(
      @NonNull String name, @NonNull List<ClientInterceptor> interceptors) {
    return cf.createChannel(name, ChannelBuilderOptions.defaults().withInterceptors(interceptors));
  }

  @Override
  public Channel createChannel(@NonNull String name) {
    return cf.createChannel(name);
  }
}
