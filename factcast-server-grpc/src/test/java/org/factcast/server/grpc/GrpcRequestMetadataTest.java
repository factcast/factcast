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

import static org.assertj.core.api.Assertions.assertThat;

import io.grpc.Metadata;
import org.factcast.grpc.api.GrpcConstants;
import org.factcast.grpc.api.Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GrpcRequestMetadataTest {

  private GrpcRequestMetadata underTest;

  @Nested
  class WhenCatchupingBatch {
    @BeforeEach
    void setup() {}

    @Test
    void respectsMinimum() {
      Metadata headers = new Metadata();
      headers.put(Headers.CLIENT_MAX_INBOUND_MESSAGE_SIZE, "127");

      underTest = new GrpcRequestMetadata(headers);

      assertThat(underTest.clientMaxInboundMessageSize())
          .isEqualTo(GrpcConstants.MIN_CLIENT_INBOUND_MESSAGE_SIZE);
    }

    @Test
    void respectsMaximum() {
      Metadata headers = new Metadata();
      headers.put(
          Headers.CLIENT_MAX_INBOUND_MESSAGE_SIZE,
          String.valueOf(GrpcConstants.MAX_CLIENT_INBOUND_MESSAGE_SIZE + 100000));

      underTest = new GrpcRequestMetadata(headers);

      assertThat(underTest.clientMaxInboundMessageSize())
          .isEqualTo(GrpcConstants.MAX_CLIENT_INBOUND_MESSAGE_SIZE);
    }

    @Test
    void extractsUnset() {
      Metadata headers = new Metadata();
      underTest = new GrpcRequestMetadata(headers);

      assertThat(underTest.clientMaxInboundMessageSize())
          .isEqualTo(GrpcConstants.DEFAULT_CLIENT_INBOUND_MESSAGE_SIZE);
    }
  }

  @Nested
  class WhenSupportsingFastForward {
    @BeforeEach
    void setup() {}

    @Test
    void extracts() {
      Metadata headers = new Metadata();
      headers.put(Headers.FAST_FORWARD, "true");

      underTest = new GrpcRequestMetadata(headers);

      assertThat(underTest.supportsFastForward()).isTrue();
    }

    @Test
    void extractsUnset() {
      Metadata headers = new Metadata();
      underTest = new GrpcRequestMetadata(headers);

      assertThat(underTest.supportsFastForward()).isFalse();
    }
  }

  @Nested
  class WhenForingTest {
    @BeforeEach
    void setup() {}

    @Test
    void createForTest() {
      GrpcRequestMetadata t = GrpcRequestMetadata.forTest();
      assertThat(t.clientId()).isEmpty();
      assertThat(t.supportsFastForward()).isTrue();
    }
  }

  @Nested
  class WhenConsumeringId {
    @BeforeEach
    void setup() {}

    @Test
    void extracts() {
      Metadata headers = new Metadata();
      headers.put(Headers.CLIENT_ID, "narf");

      underTest = new GrpcRequestMetadata(headers);

      assertThat(underTest.clientId()).isPresent().hasValue("narf");
    }

    @Test
    void extractsUnset() {
      Metadata headers = new Metadata();
      underTest = new GrpcRequestMetadata(headers);

      assertThat(underTest.clientId()).isEmpty();
    }

    @Test
    void testGettingClientIdAsString_noneSet() {
      Metadata headers = new Metadata();
      underTest = new GrpcRequestMetadata(headers);
      assertThat(underTest.clientIdAsString()).isEqualTo(GrpcRequestMetadata.UNKNOWN);
    }

    @Test
    void testGettingClientIdAsString_set() {
      Metadata headers = new Metadata();
      headers.put(Headers.CLIENT_ID, "narf");
      underTest = new GrpcRequestMetadata(headers);
      assertThat(underTest.clientIdAsString()).isEqualTo("narf");
    }

    @Test
    void testGettingClientVersionAsString_noneSet() {
      Metadata headers = new Metadata();
      underTest = new GrpcRequestMetadata(headers);
      assertThat(underTest.clientVersionAsString()).isEqualTo(GrpcRequestMetadata.UNKNOWN);
    }

    @Test
    void testGettingClientVersionAsString_set() {
      Metadata headers = new Metadata();
      headers.put(Headers.CLIENT_VERSION, "3.11");
      underTest = new GrpcRequestMetadata(headers);
      assertThat(underTest.clientVersionAsString()).isEqualTo("3.11");
    }
  }
}
