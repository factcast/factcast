/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.core.snap.local;

import com.google.common.io.CountingOutputStream;
import java.io.BufferedOutputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.factcast.core.snap.Snapshot;

@UtilityClass
class SnapshotSerializationHelper {

  @SneakyThrows
  long serializeTo(@NonNull Snapshot s, @NonNull OutputStream os) {
    try (BufferedOutputStream b = new BufferedOutputStream(os);
        CountingOutputStream c = new CountingOutputStream(b);
        ObjectOutputStream out = new ObjectOutputStream(c); ) {
      out.writeObject(s);
      out.flush();
      return c.getCount();
    }
  }
}
