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

import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;

@SuppressWarnings("ReassignedVariable")
@RequiredArgsConstructor
class GrpcStubsImpl implements GrpcStubs {

  private final @NonNull Channel channel;
  private final Metadata meta;
  private final CallCredentials basic;
  private RemoteFactStoreGrpc.RemoteFactStoreBlockingStub compressedBlocking;
  private RemoteFactStoreGrpc.RemoteFactStoreBlockingStub uncompressedBlocking;
  private RemoteFactStoreGrpc.RemoteFactStoreStub compressedNonBlocking;

  public GrpcStubsImpl(
      @NonNull FactCastGrpcChannelFactory channelFactory,
      @NonNull String channelName,
      @NonNull Metadata meta,
      @Nullable CallCredentials basic) {
    this.meta = meta;
    this.basic = basic;
    this.channel = channelFactory.createChannel(channelName);

    resetStubs();
  }

  @NonNull
  private RemoteFactStoreGrpc.RemoteFactStoreBlockingStub createBlockingStub() {
    RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub =
        RemoteFactStoreGrpc.newBlockingStub(channel);
    if (basic != null) stub = stub.withCallCredentials(basic);
    return stub;
  }

  @NonNull
  private RemoteFactStoreGrpc.RemoteFactStoreStub createStub() {
    RemoteFactStoreGrpc.RemoteFactStoreStub remoteFactStoreStub =
        RemoteFactStoreGrpc.newStub(channel);
    if (basic != null) remoteFactStoreStub = remoteFactStoreStub.withCallCredentials(basic);
    return remoteFactStoreStub;
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreBlockingStub uncompressedBlocking() {
    return uncompressedBlocking;
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreBlockingStub blocking() {
    return compressedBlocking;
  }

  @Override
  @NonNull
  public RemoteFactStoreGrpc.RemoteFactStoreStub nonBlocking() {
    return compressedNonBlocking;
  }

  @Override
  public void resetStubs() {
    ClientInterceptor clientInterceptor = MetadataUtils.newAttachHeadersInterceptor(meta);

    uncompressedBlocking = createBlockingStub().withInterceptors(clientInterceptor);
    compressedBlocking = createBlockingStub().withInterceptors(clientInterceptor);
    compressedNonBlocking = createStub().withInterceptors(clientInterceptor);
  }

  @Override
  public void setCompression(@NonNull String compressionId) {
    compressedBlocking =
        createBlockingStub()
            .withInterceptors(
                MetadataUtils.newAttachHeadersInterceptor(forCompression(compressionId)))
            .withCompression(compressionId);
    compressedNonBlocking =
        createStub()
            .withInterceptors(
                MetadataUtils.newAttachHeadersInterceptor(forCompression(compressionId)))
            .withCompression(compressionId);
  }

  private Metadata forCompression(String compressionId) {
    Metadata m = new Metadata();
    m.put(Headers.MESSAGE_COMPRESSION, compressionId);
    m.merge(this.meta);
    return m;
  }
}
