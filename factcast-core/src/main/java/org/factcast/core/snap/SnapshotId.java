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

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.NonNull;
import lombok.Value;

/**
 * This belongs to factus however we have clients out there that already have serialized versions of
 * it with this package.
 */
@Value(staticConstructor = "of")
@Deprecated
public class SnapshotId implements Serializable {
  @Serial private static final long serialVersionUID = -3207528229703207635L;
  @NonNull String key;
  @NonNull UUID uuid;
}
