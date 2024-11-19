/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.factus.projection.parameter;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import lombok.NonNull;

public class HandlerParameterContributors implements Iterable<HandlerParameterContributor> {

  private final List<HandlerParameterContributor> contributors;

  public HandlerParameterContributors() {
    this.contributors = new CopyOnWriteArrayList<>();
    contributors.add(new DefaultHandlerParameterContributor());
  }

  private HandlerParameterContributors(
      @NonNull List<HandlerParameterContributor> contribs,
      @NonNull HandlerParameterContributor topPrio) {
    this.contributors = new CopyOnWriteArrayList<>();
    this.contributors.add(topPrio);
    this.contributors.addAll(contribs);
  }

  @Override
  @NonNull
  public Iterator<HandlerParameterContributor> iterator() {
    return contributors.iterator();
  }

  @NonNull
  public Stream<HandlerParameterContributor> stream() {
    return this.contributors.stream();
  }

  @NonNull
  public HandlerParameterContributors withHighestPrio(
      @NonNull HandlerParameterContributor topPrioContributor) {
    return new HandlerParameterContributors(contributors, topPrioContributor);
  }
}
