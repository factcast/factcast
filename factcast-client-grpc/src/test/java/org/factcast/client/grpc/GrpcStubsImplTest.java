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

import io.grpc.CallCredentials;
import io.grpc.Channel;
import io.grpc.ClientInterceptor;
import io.grpc.Metadata;
import lombok.NonNull;
import org.assertj.core.api.Assertions;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcStubsImplTest {

  private static final String CHANNEL_NAME = "factstore";
  @Mock private @NonNull Channel channel;
  @Mock private CallCredentials basic;
  @Mock private FactCastGrpcChannelFactory factory;
  @Mock private Metadata meta;
  @Mock private CallCredentials creds;
  private GrpcStubsImpl underTest;

  @Nested
  class WhenCreating {

    private static final String COMP = "COMP";

    @BeforeEach
    void setup() {
      when(factory.createChannel(CHANNEL_NAME)).thenReturn(channel);
    }

    @Test
    void blockingHasMeta() {
      underTest = new GrpcStubsImpl(factory, CHANNEL_NAME, meta, null);
      RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub =
          mock(RemoteFactStoreGrpc.RemoteFactStoreBlockingStub.class);
      underTest.configure(stub);
      ArgumentCaptor<ClientInterceptor> captor = ArgumentCaptor.forClass(ClientInterceptor.class);
      verify(stub).withInterceptors(captor.capture());

      Assertions.assertThat(captor.getValue()).hasFieldOrPropertyWithValue("extraHeaders", meta);
    }

    @Test
    void nonBlockingHasMeta() {
      underTest = new GrpcStubsImpl(factory, CHANNEL_NAME, meta, null);
      RemoteFactStoreGrpc.RemoteFactStoreStub stub =
          mock(RemoteFactStoreGrpc.RemoteFactStoreStub.class);
      underTest.configure(stub);
      ArgumentCaptor<ClientInterceptor> captor = ArgumentCaptor.forClass(ClientInterceptor.class);
      verify(stub).withInterceptors(captor.capture());

      Assertions.assertThat(captor.getValue()).hasFieldOrPropertyWithValue("extraHeaders", meta);
    }

    @Test
    void withCredentials() {
      underTest = new GrpcStubsImpl(factory, CHANNEL_NAME, meta, creds);
      {
        RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub = underTest.uncompressedBlocking();
        Assertions.assertThat(stub.getCallOptions().getCredentials()).isSameAs(creds);
      }
      {
        RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub = underTest.blocking();
        Assertions.assertThat(stub.getCallOptions().getCredentials()).isSameAs(creds);
      }
      {
        RemoteFactStoreGrpc.RemoteFactStoreStub stub = underTest.nonBlocking();
        Assertions.assertThat(stub.getCallOptions().getCredentials()).isSameAs(creds);
      }
    }

    @Test
    void withoutCredentials() {
      underTest = new GrpcStubsImpl(factory, CHANNEL_NAME, meta, null);
      {
        RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub = underTest.uncompressedBlocking();
        Assertions.assertThat(stub.getCallOptions().getCredentials()).isNull();
      }
      {
        RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub = underTest.blocking();
        Assertions.assertThat(stub.getCallOptions().getCredentials()).isNull();
      }
      {
        RemoteFactStoreGrpc.RemoteFactStoreStub stub = underTest.nonBlocking();
        Assertions.assertThat(stub.getCallOptions().getCredentials()).isNull();
      }
    }

    @Test
    void withCompression() {
      underTest = new GrpcStubsImpl(factory, CHANNEL_NAME, meta, creds);
      underTest.compression(COMP);

      {
        RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub = underTest.uncompressedBlocking();
        Assertions.assertThat(stub.getCallOptions().getCompressor()).isNull();
      }
      {
        RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub = underTest.blocking();
        Assertions.assertThat(stub.getCallOptions().getCompressor()).isEqualTo(COMP);
      }
      {
        RemoteFactStoreGrpc.RemoteFactStoreStub stub = underTest.nonBlocking();
        Assertions.assertThat(stub.getCallOptions().getCompressor()).isEqualTo(COMP);
      }
    }
  }
}
