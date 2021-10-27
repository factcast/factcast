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

import java.util.List;
import java.util.Optional;

import org.factcast.core.Fact;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

/**
 * This class needs to stay final and have package-protected constructors, as is this the only way
 * we can detect if {@link Attempt#publish(Fact, Fact...)} was forgotten. Otherwise, this detection
 * could be circumvented by creating this class or a subclass and returning this.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class IntermediatePublishResult {

  @Getter @NonNull final List<Fact> factsToPublish;

  @Setter @NonNull private Runnable andThen = null;

  public Optional<Runnable> andThen() {
    return Optional.ofNullable(andThen);
  }
}
