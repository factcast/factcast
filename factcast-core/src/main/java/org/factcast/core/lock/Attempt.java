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
package org.factcast.core.lock;

import java.util.*;
import lombok.NonNull;
import org.factcast.core.Fact;

@FunctionalInterface
public interface Attempt {
  IntermediatePublishResult call() throws AttemptAbortedException;

  /**
   * this is only a convenience method. You can choose to throw AttemptAbortedException from your
   * lambda yourself or use custom subclasses in order to pass additional info out of your lamdba.
   *
   * @param msg String message to be passed into Exception
   * @throws AttemptAbortedException inevitably thrown
   */
  static IntermediatePublishResult abort(@NonNull String msg) throws AttemptAbortedException {
    throw new AttemptAbortedException(msg);
  }

  static IntermediatePublishResult publish(@NonNull List<Fact> factsToPublish) {
    if (factsToPublish.isEmpty())
      throw new IllegalArgumentException("List of Facts to publish must not be empty");
    return new IntermediatePublishResult(factsToPublish, false);
  }

  static IntermediatePublishResult publishUnlessEmpty(
      @NonNull List<Fact> factsToPublishOrEmptyList) {
    if (factsToPublishOrEmptyList.isEmpty()) return withoutPublication();
    else return publish(factsToPublishOrEmptyList);
  }

  static IntermediatePublishResult withoutPublication() {
    return new IntermediatePublishResult(Collections.emptyList(), true);
  }

  // convenience
  static IntermediatePublishResult publish(@NonNull Fact f, Fact... other) {
    List<Fact> l = new LinkedList<>();
    l.add(f);
    l.addAll(Arrays.asList(other));
    return publish(l);
  }
}
