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
package org.factcast.server.grpc;

import com.google.common.annotations.VisibleForTesting;
import io.grpc.Status;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.grpc.api.conv.ProtoConverter;

public class StagedFacts {

  private final int maxInboundBytes;
  private final int targetBytes;
  @Getter private int currentBytes;
  private final List<Fact> staged = new ArrayList<>(128);

  /** The default target remains 90% of the client-advertised inbound limit. */
  StagedFacts(int maxInboundBytes) {
    this(maxInboundBytes, 90);
  }

  StagedFacts(int maxInboundBytes, int targetPercent) {
    if (targetPercent < 1 || targetPercent > 90) {
      throw new IllegalArgumentException("batch target percent must be between 1 and 90");
    }
    this.maxInboundBytes = maxInboundBytes;
    targetBytes = maxInboundBytes - (int) ((long) maxInboundBytes * (100 - targetPercent) / 100);
  }

  public boolean add(@NonNull Fact fact) {
    int bytes = byteSizeOf(fact);
    if (staged.isEmpty() && bytes >= targetBytes) {
      int exactBytes =
          new ProtoConverter().createNotificationFor(List.of(fact)).getSerializedSize();
      if (exactBytes > maxInboundBytes) {
        throw Status.RESOURCE_EXHAUSTED
            .withDescription(
                "Fact notification requires "
                    + exactBytes
                    + " bytes, exceeding client inbound limit of "
                    + maxInboundBytes
                    + " bytes")
            .asRuntimeException();
      }
    } else if (!staged.isEmpty() && currentBytes + bytes >= targetBytes) {
      return false;
    }
    staged.add(fact);
    currentBytes += bytes;
    return true;
  }

  @VisibleForTesting
  int byteSizeOf(@NonNull Fact fact) {
    return fact.jsonPayload().getBytes(StandardCharsets.UTF_8).length
        + fact.jsonHeader().getBytes(StandardCharsets.UTF_8).length
        + 8; // to compensate for overhead of protobuf
  }

  public boolean isEmpty() {
    return staged.isEmpty();
  }

  /** size of the array (number of facts) */
  public int size() {
    return staged.size();
  }

  public List<Fact> popAll() {
    try {
      return List.copyOf(staged);
    } finally {
      staged.clear();
      currentBytes = 0;
    }
  }
}
