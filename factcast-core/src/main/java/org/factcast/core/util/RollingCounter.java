/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.core.util;

import java.util.concurrent.atomic.AtomicLong;

public class RollingCounter {
  private final long initial;
  private final AtomicLong count;

  public RollingCounter() {
    this(0);
  }

  public RollingCounter(long i) {
    count = new AtomicLong(i);
    initial = i;
  }

  public long getAndIncrement() {
    return count.getAndUpdate(v -> v == Long.MAX_VALUE ? initial : v + 1);
  }
}
