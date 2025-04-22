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

import lombok.*;
import org.factcast.core.Fact;

@Getter
public class ListObserver extends AbstractListObserver {
  private int limit;
  private int offset;

  public ListObserver(int limit, int offset) {
    this.limit = limit;
    this.offset = offset;
  }

  public ListObserver(Long untilSerial, int limit, int offset) {
    this.untilSerial = untilSerial;
    this.limit = limit;
    this.offset = offset;
  }

  @Override
  public void onNext(@NonNull Fact element) {
    if (isComplete(element)) {
      throw new LimitReachedException();
    }

    if (offset > 0) {
      offset--;
    } else {
      limit--;
      list().add(0, element);
    }
  }

  @Override
  public final void onError(@NonNull Throwable exception) {
    if (!LimitReachedException.matches(exception)) {
      handleError(exception);
    }
  }

  boolean isComplete(@NonNull Fact fact) {
    return limit <= 0 || hasReachedTheLimitSerial(fact);
  }
}
