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
package org.factcast.store.internal.checkpoint;

import javax.sql.DataSource;
import lombok.NonNull;

/**
 * Supplies commit-safe stream boundaries.
 *
 * <p>A fact query may fast-forward through a returned boundary only when the query itself is
 * bounded by that value.
 */
public interface FactStreamCheckpointProvider {

  /** Advances or refreshes the checkpoint through the provider's default data source. */
  @NonNull
  FactStreamCheckpoint advance();

  /** Returns the last checkpoint observed by {@link #advance()} without database access. */
  @NonNull
  FactStreamCheckpoint current();

  /** Reads the persisted checkpoint through a specific data source without advancing it. */
  @NonNull
  FactStreamCheckpoint read(@NonNull DataSource dataSource);
}
