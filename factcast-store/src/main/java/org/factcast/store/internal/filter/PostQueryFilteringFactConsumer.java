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
package org.factcast.store.internal.filter;

import java.util.function.Consumer;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.store.internal.PostQueryMatcher;

@RequiredArgsConstructor
public class PostQueryFilteringFactConsumer implements Consumer<Fact> {

  @NonNull private final Consumer<Fact> parent;
  @NonNull private final PostQueryMatcher pqm;

  @Override
  public void accept(Fact fact) {
    if (pqm.canBeSkipped() || fact == null || pqm.test(fact)) parent.accept(fact);
  }
}
