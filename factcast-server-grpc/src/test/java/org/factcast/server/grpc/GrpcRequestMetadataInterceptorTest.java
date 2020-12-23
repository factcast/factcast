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

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcRequestMetadataInterceptorTest {

  @Mock private GrpcRequestMetadata scopedBean;
  @InjectMocks private GrpcRequestMetadataInterceptor underTest;

  @Nested
  class WhenInterceptingCall {
    @Mock private ServerCall call;
    @Mock private ServerCallHandler next;

    @BeforeEach
    void setup() {}

    @Test
    void providesHeaders() {
      Metadata headers = mock(Metadata.class);
      underTest.interceptCall(call, headers, next);

      verify(scopedBean).headers(same(headers));
    }
  }
}
