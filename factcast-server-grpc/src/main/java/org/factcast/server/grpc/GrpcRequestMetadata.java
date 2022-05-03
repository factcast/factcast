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
import io.grpc.Metadata;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Setter;
import org.factcast.grpc.api.Headers;

public class GrpcRequestMetadata {

  private static final String UNKNOWN = "unknown";

  @Setter(AccessLevel.PROTECTED)
  Metadata headers;

  OptionalInt catchupBatch() {
    return Stream.of(headers.get(Headers.CATCHUP_BATCHSIZE))
        .filter(Objects::nonNull)
        .mapToInt(Integer::parseInt)
        .findFirst();
  }

  boolean supportsFastForward() {
    return headers.containsKey(Headers.FAST_FORWARD);
  }

  @VisibleForTesting
  public static GrpcRequestMetadata forTest() {
    GrpcRequestMetadata grpcRequestMetadata = new GrpcRequestMetadata();
    grpcRequestMetadata.headers = new Metadata();
    grpcRequestMetadata.headers.put(Headers.FAST_FORWARD, "true");
    return grpcRequestMetadata;
  }

  public Optional<String> clientId() {
    return Optional.ofNullable(headers).map(headers -> headers.get(Headers.CLIENT_ID));
  }

  public String clientIdAsString() {
    return clientId().orElse(UNKNOWN);
  }
}
