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
import lombok.Value;
import org.factcast.core.Fact;
import org.factcast.core.FactStreamPosition;
import org.factcast.core.subscription.FactStreamInfo;
import org.factcast.core.subscription.SubscriptionImpl;

public sealed interface Signal
    permits Signal.FlushSignal,
        Signal.CatchupSignal,
        Signal.CompleteSignal,
        Signal.FastForwardSignal,
        Signal.FactStreamInfoSignal,
        Signal.FactSignal,
        Signal.ErrorSignal {

  void pass(SubscriptionImpl target);

  static Signal flush() {
    return new FlushSignal();
  }

  static Signal catchup() {
    return new CatchupSignal();
  }

  static Signal complete() {
    return new CompleteSignal();
  }

  static Signal of(FactStreamPosition ffwd) {
    return new FastForwardSignal(ffwd);
  }

  static Signal of(Fact fact) {
    return new FactSignal(fact);
  }

  static Signal of(FactStreamInfo info) {
    return new FactStreamInfoSignal(info);
  }

  static Signal of(Throwable e) {
    return new ErrorSignal(e);
  }

  final class FlushSignal implements Signal {
    @Override
    public void pass(@NonNull SubscriptionImpl target) {
      // TODO not yet implemented, add flush on sub/FO
    }
  }

  final class CatchupSignal implements Signal {
    @Override
    public void pass(SubscriptionImpl target) {
      target.notifyCatchup();
    }
  }

  final class CompleteSignal implements Signal {
    @Override
    public void pass(SubscriptionImpl target) {
      target.notifyComplete();
    }
  }

  @Value
  class FastForwardSignal implements Signal {
    @NonNull FactStreamPosition factStreamPosition;

    @Override
    public void pass(SubscriptionImpl target) {
      target.notifyFastForward(factStreamPosition);
    }
  }

  @Value
  class FactStreamInfoSignal implements Signal {
    @NonNull FactStreamInfo factStreamInfo;

    @Override
    public void pass(SubscriptionImpl target) {
      target.notifyFactStreamInfo(factStreamInfo);
    }
  }

  @Value
  class FactSignal implements Signal {
    @NonNull Fact fact;

    @Override
    public void pass(SubscriptionImpl target) {
      target.notifyElement(fact);
    }
  }

  @Value
  class ErrorSignal implements Signal {
    @NonNull Throwable error;

    @Override
    public void pass(SubscriptionImpl target) {
      target.notifyError(error);
    }
  }
}
