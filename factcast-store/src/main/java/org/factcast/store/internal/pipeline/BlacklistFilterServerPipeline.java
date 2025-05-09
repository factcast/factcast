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
import org.factcast.store.internal.filter.blacklist.Blacklist;

@Slf4j
public class BlacklistFilterServerPipeline extends AbstractServerPipeline {
  @NonNull final Blacklist blacklist;

  public BlacklistFilterServerPipeline(
      @NonNull ServerPipeline parent, @NonNull Blacklist blacklist) {
    super(parent);
    this.blacklist = blacklist;
  }

  @Override
  public void process(@NonNull Signal s) {
    if (s instanceof Signal.FactSignal fs) {
      Fact fact = fs.fact();
      if (!blacklist.isBlocked(fact.header().id())) {
        parent.process(s);
      } else {
        log.trace("removing blacklisted fact from pipeline {}", fact);
      }
    } else {
      parent.process(s);
    }
  }
}
