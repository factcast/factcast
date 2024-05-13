package org.factcast.client.grpc;

import io.grpc.Channel;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FactCastGrpcStubsFactoryImplTest {

  @Mock
  Channel channel;

  final FactCastGrpcStubsFactory uut = new FactCastGrpcStubsFactoryImpl();

  @Test
  public void createsBlockingStub() {
    try (MockedStatic<RemoteFactStoreGrpc> mock = mockStatic(RemoteFactStoreGrpc.class)) {
      RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub = mock(RemoteFactStoreGrpc.RemoteFactStoreBlockingStub.class);
      mock.when(() -> RemoteFactStoreGrpc.newBlockingStub(channel)).thenReturn(stub);
      assertEquals(stub, uut.createBlockingStub(channel));
    }
  }

  @Test
  public void createsStub() {
    try (MockedStatic<RemoteFactStoreGrpc> mock = mockStatic(RemoteFactStoreGrpc.class)) {
      RemoteFactStoreGrpc.RemoteFactStoreStub stub = mock(RemoteFactStoreGrpc.RemoteFactStoreStub.class);
      mock.when(() -> RemoteFactStoreGrpc.newStub(channel)).thenReturn(stub);
      assertEquals(stub, uut.createStub(channel));
    }
  }
}