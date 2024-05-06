package org.factcast.client.grpc;

import io.grpc.Channel;
import lombok.NonNull;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;

public interface FactCastGrpcStubsFactory {

  @NonNull
  RemoteFactStoreGrpc.RemoteFactStoreBlockingStub createBlockingStub(@NonNull Channel channel);

  @NonNull
  RemoteFactStoreGrpc.RemoteFactStoreStub createStub(@NonNull Channel channel);
}
