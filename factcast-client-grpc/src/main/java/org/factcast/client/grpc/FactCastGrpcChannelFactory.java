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
package org.factcast.client.grpc;

import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.ManagedChannel;
import java.util.List;
import org.springframework.grpc.client.GrpcChannelFactory;

public interface FactCastGrpcChannelFactory extends AutoCloseable {

  /**
   * Creates a new channel for the given service name. The returned channel will use all globally
   * registered {@link ClientInterceptor}s.
   *
   * <p><b>Note:</b> The underlying implementation might reuse existing {@link ManagedChannel}s
   * allow connection reuse.
   *
   * @param name The name of the service.
   * @return The newly created channel for the given service.
   */
  Channel createChannel(String name);

  /**
   * Creates a new channel for the given service name. The returned channel will use all globally
   * registered {@link ClientInterceptor}s.
   *
   * <p><b>Note:</b> The underlying implementation might reuse existing {@link ManagedChannel}s
   * allow connection reuse.
   *
   * <p><b>Note:</b> The given interceptors will be applied after the global interceptors. But the
   * interceptors that were applied last, will be called first.
   *
   * @param name The name of the service.
   * @param interceptors A list of additional client interceptors that should be added to the
   *     channel.
   * @return The newly created channel for the given service.
   */
  @SuppressWarnings("unused")
  Channel createChannel(String name, List<ClientInterceptor> interceptors);

  static FactCastGrpcChannelFactory createDefault(GrpcChannelFactory cf) {
    return new FactCastGrpcChannelFactoryImpl(cf);
  }
}
