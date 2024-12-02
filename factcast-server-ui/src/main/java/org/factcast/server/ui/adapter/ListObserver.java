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

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Getter;
import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.subscription.observer.FactObserver;
import org.slf4j.LoggerFactory;

@Getter
public class ListObserver implements FactObserver {
  private int limit;
  private int offset;
  @Nullable private Long untilSerial;
  private final List<Fact> list = new ArrayList<>();

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
    if (isComplete() || hasReachedTheLimitSerial(element)) {
      throw new LimitReachedException();
    }

    if (offset > 0) {
      offset--;
    } else {
      limit--;
      list.add(0, element);
    }
  }

  @Override
  public final void onError(@NonNull Throwable exception) {
    if (!LimitReachedException.matches(exception)) {
      handleError(exception);
    }
  }

  protected void handleError(@NonNull Throwable exception) {
    LoggerFactory.getLogger(FactObserver.class).warn("Unhandled onError:", exception);
  }

  boolean isComplete() {
    return limit <= 0;
  }

  boolean hasReachedTheLimitSerial(Fact fact) {
    Long serial = fact.header().serial();
    return untilSerial != null && serial != null && untilSerial < serial;
  }
}
