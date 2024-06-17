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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactCastGrpcStubsFactoryImplTest {

  //  @Mock Channel channel;
  //
  //  final GrpcStubs uut = new GrpcStubsImpl();
  //
  //  @Test
  //  void createsBlockingStub() {
  //    try (MockedStatic<RemoteFactStoreGrpc> mock = mockStatic(RemoteFactStoreGrpc.class)) {
  //      RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub =
  //          mock(RemoteFactStoreGrpc.RemoteFactStoreBlockingStub.class);
  //      mock.when(() -> RemoteFactStoreGrpc.newBlockingStub(channel)).thenReturn(stub);
  //      assertEquals(stub, uut.createBlockingStub(channel));
  //    }
  //  }
  //
  //  @Test
  //  void createsStub() {
  //    try (MockedStatic<RemoteFactStoreGrpc> mock = mockStatic(RemoteFactStoreGrpc.class)) {
  //      RemoteFactStoreGrpc.RemoteFactStoreStub stub =
  //          mock(RemoteFactStoreGrpc.RemoteFactStoreStub.class);
  //      mock.when(() -> RemoteFactStoreGrpc.newStub(channel)).thenReturn(stub);
  //      assertEquals(stub, uut.createStub(channel));
  //    }
  //  }
}
