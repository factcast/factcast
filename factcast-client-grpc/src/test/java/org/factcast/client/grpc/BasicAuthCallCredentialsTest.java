/*
 * Copyright © 2017-2026 factcast.org
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import io.grpc.CallCredentials;
import io.grpc.Metadata;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BasicAuthCallCredentialsTest {

  @Mock CallCredentials.RequestInfo requestInfo;
  @Mock Executor executor;
  @Mock CallCredentials.MetadataApplier metadataApplier;

  @Test
  void testApplyRequestMetadata() {
    String username = "user";
    String password = "pass";
    byte[] encoded =
        Base64.getEncoder().encode((username + ":" + password).getBytes(StandardCharsets.UTF_8));

    BasicAuthCallCredentials credentials = BasicAuthCallCredentials.of("user", "pass");
    credentials.applyRequestMetadata(requestInfo, executor, metadataApplier);

    String expectedAuth = "Basic " + new String(encoded, StandardCharsets.UTF_8);
    assertThat(credentials.authorization).isEqualTo(expectedAuth);
    verify(metadataApplier)
        .apply(
            argThat(
                metadata -> {
                  String authHeader =
                      metadata.get(
                          Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER));
                  return expectedAuth.equals(authHeader);
                }));
  }
}
