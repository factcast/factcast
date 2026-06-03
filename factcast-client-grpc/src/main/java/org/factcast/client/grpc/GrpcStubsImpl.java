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

import com.google.common.annotations.VisibleForTesting;
import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.Deadline;
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import jakarta.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;

@SuppressWarnings("ReassignedVariable")
@RequiredArgsConstructor
public class GrpcStubsImpl implements GrpcStubs {

  private final @NonNull Channel channel;
  private final @NonNull Metadata meta;
  private final @Nullable CallCredentials basic;
  private final @NonNull FactCastGrpcClientProperties properties;

  // can change at runtime (failover)
  @Setter @Nullable private String compression;

  public GrpcStubsImpl(
      @NonNull FactCastGrpcChannelFactory channelFactory,
      @NonNull String channelName,
      @NonNull Metadata meta,
      @Nullable CallCredentials basic,
      @NonNull FactCastGrpcClientProperties properties) {
    this.meta = meta;
    this.basic = basic;
    this.properties = properties;
    this.channel = channelFactory.createChannel(channelName);
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreBlockingStub uncompressedBlocking(
      @Nullable Deadline deadline) {
    return configure(RemoteFactStoreGrpc.newBlockingStub(channel), deadline);
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreBlockingStub blocking(@Nullable Deadline deadline) {
    return configureCompression(configure(RemoteFactStoreGrpc.newBlockingStub(channel), deadline));
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreStub nonBlocking() {
    return configureCompression(configure(RemoteFactStoreGrpc.newStub(channel), null));
  }

  @VisibleForTesting
  @NonNull
  <T extends AbstractStub<T>> T configure(@NonNull T stub, @Nullable Deadline deadline) {
    if (basic != null) {
      stub = stub.withCallCredentials(basic);
    }
    if (deadline != null) {
      stub = stub.withDeadline(deadline);
    }
    stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(meta));
    stub = stub.withMaxInboundMessageSize(properties.getMaxInboundMessageSize());
    return stub.withWaitForReady();
  }

  @VisibleForTesting
  @NonNull
  <T extends AbstractStub<T>> T configure(@NonNull T stub) {
    return configure(stub, null);
  }

  @VisibleForTesting
  @NonNull
  <T extends AbstractStub<T>> T configureCompression(@NonNull T stub) {
    if (compression != null) {
      return stub.withCompression(compression)
          .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(forCompression(compression)));
    } else {
      return stub;
    }
  }

  @NonNull
  private Metadata forCompression(@NonNull String compressionId) {
    Metadata m = new Metadata();
    m.put(Headers.MESSAGE_COMPRESSION, compressionId);
    return m;
  }
}
