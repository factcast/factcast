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
package org.factcast.server.ui.adapter;

import java.util.List;
import java.util.function.Consumer;
import lombok.*;
import lombok.extern.java.Log;
import org.factcast.core.Fact;

@Getter
@Log
public class UnlimitedConsumingObserver extends AbstractListObserver {
  private final int batchSize = 1000;
  private int offset;
  private final @NonNull Consumer<List<Fact>> factConsumer;
  private long processedFacts = 0;

  //  public UnlimitedConsumingObserver(int offset) {
  //    this.offset = offset;
  //  }

  public UnlimitedConsumingObserver(
      Long untilSerial, int offset, @NonNull Consumer<List<Fact>> factConsumer) {
    this.untilSerial = untilSerial;
    this.offset = offset;
    this.factConsumer = factConsumer;
  }

  @Override
  public void onNext(@NonNull Fact element) {
    if (isComplete(element)) {
      throw new LimitReachedException();
    }

    if (offset > 0) {
      offset--;
    } else {
      processedFacts++;
      if (list().size() >= batchSize) {
        factConsumer.accept(list());
        list().clear();
      } else {
        list().add(element);
      }
    }
  }

  @Override
  public void onError(@NonNull Throwable exception) {
    handleError(exception);
  }

  boolean isComplete(@NonNull Fact fact) {
    return hasReachedTheLimitSerial(fact);
  }
}
