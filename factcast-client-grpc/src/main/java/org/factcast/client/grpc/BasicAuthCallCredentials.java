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

import com.google.common.annotations.VisibleForTesting;
import io.grpc.CallCredentials;
import io.grpc.Metadata;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.Executor;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class BasicAuthCallCredentials extends CallCredentials {

  @VisibleForTesting protected final String authorization;

  public static BasicAuthCallCredentials of(@NonNull String username, @NonNull String password) {
    String auth = username + ":" + password;
    byte[] encoded;
    try {
      encoded = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Failed to encode basic authentication token", e);
    }
    String var10000 = new String(encoded, StandardCharsets.UTF_8);
    return new BasicAuthCallCredentials("Basic " + var10000);
  }

  @Override
  public void applyRequestMetadata(
      RequestInfo requestInfo, Executor executor, MetadataApplier metadataApplier) {
    Metadata extraHeaders = new Metadata();
    extraHeaders.put(
        Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER), authorization);
    metadataApplier.apply(extraHeaders);
  }
}
