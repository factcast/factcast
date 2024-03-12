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
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionImpl;
import org.jetbrains.annotations.Nullable;

// might be obsolete, if we can make subscription impl FP. We need to be caeful about the contracts
// for the client side though, so better safe than sorry, for now.
@RequiredArgsConstructor
public class BaseFactPipeline implements FactPipeline {
  final SubscriptionImpl sub;

  @Override
  public void fact(@Nullable Fact fact) {
    // TODO need to get the FO out of it, and use this instead in order to turn a null into a flush
    // here.
    if (fact != null) sub.notifyElement(fact);
  }

  @Override
  public void info(@NonNull FactStreamInfo info) {
    sub.notifyFactStreamInfo(info);
  }

  @Override
  public void fastForward(@NonNull FactStreamPosition ffwd) {
    sub.notifyFastForward(ffwd);
  }

  @Override
  public void error(@NonNull Throwable err) {
    sub.notifyError(err);
  }

  @Override
  public void catchup() {
    sub.notifyCatchup();
  }

  @Override
  public void complete() {
    sub.notifyComplete();
  }
}
