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
import io.grpc.Metadata;
import io.grpc.stub.AbstractStub;
import io.grpc.stub.MetadataUtils;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;

@SuppressWarnings("ReassignedVariable")
@RequiredArgsConstructor
class GrpcStubsImpl implements GrpcStubs {

  private final @NonNull Channel channel;
  private final @NonNull Metadata meta;
  private final @Nullable CallCredentials basic;

  @Setter
  @Accessors(chain = false)
  private String compression = null;

  public GrpcStubsImpl(
      @NonNull FactCastGrpcChannelFactory channelFactory,
      @NonNull String channelName,
      @NonNull Metadata meta,
      @Nullable CallCredentials basic) {
    this.meta = meta;
    this.basic = basic;
    this.channel = channelFactory.createChannel(channelName);
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreBlockingStub uncompressedBlocking() {
    return configure(RemoteFactStoreGrpc.newBlockingStub(channel));
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreBlockingStub blocking() {
    return configureCompression(configure(RemoteFactStoreGrpc.newBlockingStub(channel)));
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreStub nonBlocking() {
    return configureCompression(configure(RemoteFactStoreGrpc.newStub(channel)));
  }

  @VisibleForTesting
  RemoteFactStoreGrpc.RemoteFactStoreStub configure(
      RemoteFactStoreGrpc.@NonNull RemoteFactStoreStub stub) {
    if (basic != null) stub = stub.withCallCredentials(basic);
    stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(meta));
    return stub;
  }

  @VisibleForTesting
  RemoteFactStoreGrpc.RemoteFactStoreBlockingStub configure(
      RemoteFactStoreGrpc.@NonNull RemoteFactStoreBlockingStub stub) {
    if (basic != null) stub = stub.withCallCredentials(basic);
    stub = stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(meta));
    return stub;
  }

  @SuppressWarnings("unchecked")
  @VisibleForTesting
  <T extends AbstractStub<?>> T configureCompression(T stub) {
    if (compression != null)
      return (T)
          stub.withCompression(compression)
              .withInterceptors(
                  MetadataUtils.newAttachHeadersInterceptor(forCompression(compression)));
    else return stub;
  }

  private Metadata forCompression(String compressionId) {
    Metadata m = new Metadata();
    m.put(Headers.MESSAGE_COMPRESSION, compressionId);
    return m;
  }
}
