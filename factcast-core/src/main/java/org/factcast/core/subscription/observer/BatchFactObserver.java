/*
 * Copyright © 2017-2020 factcast.org
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

import java.util.List;
import lombok.Generated;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;

/**
 * Callback interface to use when subscribing to Facts from FactCast.
 *
 * @author uwe.schaefer@prisma-capacity.eu
 */
@Generated // sneakily skip coverage generation
public interface BatchFactObserver extends StreamObserver {

  void onNext(@NonNull List<Fact> element);

  @RequiredArgsConstructor
  class Adapter implements BatchFactObserver {
    final FactObserver factObserver;

    @Override
    public void onNext(@NonNull List<Fact> element) {
      element.forEach(factObserver::onNext);
    }
  }
}
