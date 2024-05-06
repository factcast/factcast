package org.factcast.client.grpc;

import io.grpc.Channel;
import lombok.NonNull;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;

public class FactCastGrpcStubsFactoryImpl implements FactCastGrpcStubsFactory {

  @NonNull
  @Override
  public RemoteFactStoreGrpc.RemoteFactStoreBlockingStub createBlockingStub(@NonNull Channel channel) {
    return RemoteFactStoreGrpc.newBlockingStub(channel);
  }

  @NonNull
  @Override
  public RemoteFactStoreGrpc.RemoteFactStoreStub createStub(@NonNull Channel channel) {
    return RemoteFactStoreGrpc.newStub(channel);
  }
}
