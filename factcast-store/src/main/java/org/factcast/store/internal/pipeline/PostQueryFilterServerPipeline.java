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
package org.factcast.store.internal.pipeline;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.store.internal.PostQueryMatcher;

@Slf4j
public class PostQueryFilterServerPipeline extends AbstractServerPipeline {
  @NonNull final PostQueryMatcher matcher;

  public PostQueryFilterServerPipeline(
      @NonNull ServerPipeline parent, @NonNull PostQueryMatcher matcher) {
    super(parent);
    this.matcher = matcher;
  }

  @Override
  public void process(@NonNull Signal s) {
    if (s instanceof Signal.FactSignal fs) {
      Fact fact = fs.fact();
      if (matcher.canBeSkipped() || matcher.test(fact)) {
        parent.process(s);
      } else {
        log.trace("removing unmatched fact from pipeline {}", fact);
      }
    } else {
      parent.process(s);
    }
  }
}
