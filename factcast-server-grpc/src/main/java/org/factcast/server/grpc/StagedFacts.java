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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.Fact;

public class StagedFacts {

  private final int maxBytes;
  @Getter private int currentBytes;
  private final List<Fact> staged = new ArrayList<>(128);

  /**
   * Note that the buffer will only utilize 90% of the maxBytes size. The reason is that byteSizeOf
   * might become inadequate at some point, if protobuf encoding changes.
   *
   * @param maxBytes maximum number of bytes the buffer should hold
   */
  StagedFacts(int maxBytes) {
    this.maxBytes = maxBytes - (int) (maxBytes * .1);
  }

  public boolean add(@NonNull Fact fact) {
    int bytes = byteSizeOf(fact);
    if (currentBytes + bytes >= maxBytes) {
      return false;
    } else {
      staged.add(fact);
      currentBytes += bytes;
      return true;
    }
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
