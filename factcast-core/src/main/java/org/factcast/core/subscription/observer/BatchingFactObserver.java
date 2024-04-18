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
package org.factcast.core.subscription.observer;

import com.google.common.base.Preconditions;
import java.util.ArrayList;
import java.util.List;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.Flushable;

/**
 * This is supposed to be used on the client side only. It'll recieve a flush() call when the GRPC
 * layer fully processed one Message, so that the max number of Facts to buffer is ultimately
 * limited by the size of a GRPC message
 */
public abstract class BatchingFactObserver implements FactObserver, Flushable {
  /** for example could come from transaction size */
  final int maxNumberOfFactsToBuffer;

  private List<Fact> buffer;

  protected BatchingFactObserver(int maxNumberOfFactsToBuffer) {
    Preconditions.checkArgument(maxNumberOfFactsToBuffer > 0);
    this.maxNumberOfFactsToBuffer = maxNumberOfFactsToBuffer;
    buffer = new ArrayList<>(maxNumberOfFactsToBuffer);
  }

  @Override
  public void onNext(@NonNull Fact element) {
    buffer.add(element);
    if (buffer.size() >= maxNumberOfFactsToBuffer) flush();
  }

  @Override
  public void flush() {
    FactObserver.super.flush();

    if (!buffer.isEmpty()) {
      List<Fact> current = buffer;
      buffer = new ArrayList<>(maxNumberOfFactsToBuffer);
      onNext(current);
    }
  }

  public abstract void onNext(List<Fact> batchOfFacts);
}
