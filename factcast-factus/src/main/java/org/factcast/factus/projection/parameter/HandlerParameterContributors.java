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
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Stream;
import lombok.NonNull;
import org.factcast.factus.event.EventSerializer;

public class HandlerParameterContributors implements Iterable<HandlerParameterContributor> {

  private final Set<HandlerParameterContributor> contributors;

  public HandlerParameterContributors(@NonNull EventSerializer ser) {
    this.contributors = new CopyOnWriteArraySet<>();
    contributors.add(new DefaultHandlerParameterContributor(ser));
  }

  @Override
  public Iterator<HandlerParameterContributor> iterator() {
    return contributors.iterator();
  }

  void add(@NonNull HandlerParameterContributor toAdd) {
    // TODO check for duplicates
    this.contributors.add(toAdd);
  }

  public Stream<HandlerParameterContributor> stream() {
    return this.contributors.stream();
  }
}
