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
package org.factcast.store.internal;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Lazily creates a SCDS and closes it */
@RequiredArgsConstructor
class PrimaryDataSourceSupplier implements AutoCloseable {
  private final Supplier<SingleConnectionDataSource> supplier;
  private SingleConnectionDataSource resolved = null;

  public synchronized SingleConnectionDataSource get() {
    if (resolved != null) return resolved;
    else {
      resolved = supplier.get();
      return resolved;
    }
  }

  public void close() {
    if (resolved != null) resolved.close();
  }
}
