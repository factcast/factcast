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
package org.factcast.server.grpc;

import static org.mockito.Mockito.*;

import io.grpc.CompressorRegistry;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.Headers;
import org.junit.jupiter.api.*;

class GrpcCompressionInterceptorTest {

  final GrpcCompressionInterceptor uut =
      new GrpcCompressionInterceptor(
          new CompressionCodecs(CompressorRegistry.getDefaultInstance()));

  @Test
  void interceptCallWithoutCompression() {
    ServerCall call = mock(ServerCall.class);
    Metadata metadata = mock(Metadata.class);
    ServerCallHandler next = mock(ServerCallHandler.class);

    uut.interceptCall(call, metadata, next);

    verify(next).startCall(call, metadata);
    verifyNoMoreInteractions(call);
    verifyNoMoreInteractions(next);
  }

  @Test
  void interceptCallGZip() {
    ServerCall call = mock(ServerCall.class);
    Metadata metadata = mock(Metadata.class);
    when(metadata.get(Headers.MESSAGE_COMPRESSION)).thenReturn("gzip");
    ServerCallHandler next = mock(ServerCallHandler.class);

    uut.interceptCall(call, metadata, next);

    verify(next).startCall(call, metadata);
    verify(call).setCompression("gzip");
    verify(call).setMessageCompression(false);
  }
}
