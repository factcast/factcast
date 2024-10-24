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
package org.factcast.grpc.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.*;

@ExtendWith(MockitoExtension.class)
class GrpcConstantsTest {

  @Nested
  class WhenCalculatingMaxInboundMessageSize {
    @Test
    void calculateMaxInboundMessageSize() {
      assertThat(GrpcConstants.calculateMaxInboundMessageSize(mb(1))).isEqualTo(mb(2));
      assertThat(GrpcConstants.calculateMaxInboundMessageSize(mb(2))).isEqualTo(mb(2));
      assertThat(GrpcConstants.calculateMaxInboundMessageSize(mb(3))).isEqualTo(mb(3));
      assertThat(GrpcConstants.calculateMaxInboundMessageSize(mb(34))).isEqualTo(mb(32));
    }
  }

  private int mb(int mb) {
    return mb * 1024 * 1024;
  }
}
