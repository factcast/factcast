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
package org.factcast.factus.snapshot;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.Value;
import org.factcast.factus.serializer.SnapshotSerializerId;

@Value
public class SnapshotData {

  private static final short MAGIC_BYTES = 0x09af;

  @NonNull byte[] serializedProjection;
  @NonNull SnapshotSerializerId snapshotSerializerId;
  @NonNull UUID lastFactId;

  @SuppressWarnings("deprecation")
  // TODO needed for downward comp?
  //    public static SnapshotData from(org.factcast.core.snap.Snapshot snapshot) {
  //        return new SnapshotData(snapshot.bytes(), snapshot.lastFact());
  //    }

  public static Optional<SnapshotData> from(byte[] source) {
    ByteArrayDataInput is = ByteStreams.newDataInput(source);
    if (is.readShort() == MAGIC_BYTES) {
      UUID lastFact = new UUID(is.readLong(), is.readLong());
      SnapshotSerializerId serId = SnapshotSerializerId.of(is.readUTF());
      byte[] bytes = new byte[is.readInt()];
      is.readFully(bytes);
      return Optional.of(new SnapshotData(bytes, serId, lastFact));
    }
    return Optional.empty();
  }

  public byte[] toBytes() {
    ByteArrayDataOutput os = ByteStreams.newDataOutput();
    os.writeShort(MAGIC_BYTES);
    os.writeLong(lastFactId.getMostSignificantBits());
    os.writeLong(lastFactId.getLeastSignificantBits());
    os.writeUTF(snapshotSerializerId.name());
    os.writeInt(serializedProjection.length);
    os.write(serializedProjection);
    return os.toByteArray();
  }
}
