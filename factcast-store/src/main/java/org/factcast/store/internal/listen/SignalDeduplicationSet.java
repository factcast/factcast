/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.store.internal.listen;

import java.util.Iterator;
import java.util.LinkedHashSet;
import org.factcast.store.internal.listen.PgListener.Signal;

public class SignalDeduplicationSet {
  private final LinkedHashSet<Signal> lhs;
  private final int capacity;

  public SignalDeduplicationSet(int capacity) {
    // *2 to prevent rehashing
    this.lhs = new LinkedHashSet<Signal>(capacity * 2, 0.75f);
    this.capacity = capacity;
  }

  public boolean add(Signal e) {
    synchronized (lhs) {
      if (lhs.add(e)) {
        trim(lhs);
        return true;
      }
    }
    return false;
  }

  private void trim(LinkedHashSet<Signal> lhs) {
    if (lhs.size() > capacity) {
      Iterator<Signal> i = lhs.iterator();
      i.next();
      i.remove();
    }
  }
}
