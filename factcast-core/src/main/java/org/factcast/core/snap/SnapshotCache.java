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
package org.factcast.core.snap;

import java.util.Optional;
import lombok.NonNull;

/**
 * Even though this functionality is available through factStore, we chose to extract it from the
 * factcast facade. Using factcast itself for snapshots is a convenience feature - for heavy duty
 * installations having a dedicated implementation using your favorite K/V Store is suggested in
 * order to lift this load, as well as the data from factcast.
 */
public interface SnapshotCache {

  @NonNull
  Optional<Snapshot> getSnapshot(@NonNull SnapshotId id);

  void setSnapshot(@NonNull Snapshot snapshot);

  void clearSnapshot(@NonNull SnapshotId id);
}
