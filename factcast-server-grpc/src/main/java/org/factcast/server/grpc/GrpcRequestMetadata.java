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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import io.grpc.Metadata;
import java.util.*;
import java.util.stream.*;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.grpc.api.GrpcConstants;
import org.factcast.grpc.api.Headers;

@Slf4j
@RequiredArgsConstructor
public class GrpcRequestMetadata {

  public static final String UNKNOWN = "unknown";

  private final Metadata headers;

  int clientMaxInboundMessageSize() {
    Preconditions.checkNotNull(
        headers, "GrpcRequestMetadata has not been provided with headers via Interceptor");

    int requested =
        Stream.of(headers.get(Headers.CLIENT_MAX_INBOUND_MESSAGE_SIZE))
            .filter(Objects::nonNull)
            .mapToInt(Integer::parseInt)
            .findFirst()
            .orElse(GrpcConstants.DEFAULT_CLIENT_INBOUND_MESSAGE_SIZE);

    return GrpcConstants.calculateMaxInboundMessageSize(requested);
  }

  boolean supportsFastForward() {
    return headers.containsKey(Headers.FAST_FORWARD);
  }

  @VisibleForTesting
  public static GrpcRequestMetadata forTest() {
    return forTest(1024 * 1024L);
  }

  @VisibleForTesting
  public static GrpcRequestMetadata forTest(long maxInboundMessageSize) {
    final var headers = new Metadata();
    headers.put(Headers.FAST_FORWARD, "true");
    headers.put(Headers.CLIENT_MAX_INBOUND_MESSAGE_SIZE, String.valueOf(maxInboundMessageSize));

    return new GrpcRequestMetadata(headers);
  }

  @NonNull
  public Optional<String> clientId() {
    return Optional.ofNullable(headers).map(h -> h.get(Headers.CLIENT_ID));
  }

  @NonNull
  public Optional<String> clientVersion() {
    return Optional.ofNullable(headers).map(h -> h.get(Headers.CLIENT_VERSION));
  }

  @NonNull
  public String clientIdAsString() {
    return clientId().orElse(UNKNOWN);
  }

  @NonNull
  public String clientVersionAsString() {
    return clientVersion().orElse(UNKNOWN);
  }
}
