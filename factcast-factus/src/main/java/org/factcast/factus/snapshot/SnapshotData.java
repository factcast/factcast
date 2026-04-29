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
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.NonNull;
import lombok.Value;
import org.factcast.core.snap.Snapshot;
import org.factcast.factus.serializer.SnapshotSerializerId;

@Value
public class SnapshotData {

  private static final short MAGIC_BYTES = 0x09af;

  @NonNull byte[] serializedProjection;
  @NonNull SnapshotSerializerId snapshotSerializerId;
  @NonNull UUID lastFactId;

  /**
   * @param snapshot
   * @param serId
   * @return
   * @deprecated
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public static SnapshotData from(Snapshot snapshot, @NonNull SnapshotSerializerId serId) {
    return new SnapshotData(snapshot.bytes(), serId, snapshot.lastFact());
  }

  public static Optional<SnapshotData> from(byte[] source) {
    ByteArrayDataInput is = ByteStreams.newDataInput(source);
    short magic = is.readShort();

    return Arrays.stream(Protocol.values())
        .filter(p -> magic == p.magic())
        .findFirst()
        .flatMap(p -> p.from(is));
  }

  public byte[] toBytes() {
    return Protocol.V1.toBytes(this);
  }

  @SuppressWarnings("java:S6548")
  enum Protocol {
    V1 {
      @Override
      short magic() {
        return 0x09af;
      }

      @Override
      Optional<SnapshotData> from(ByteArrayDataInput is) {
        UUID lastFact = new UUID(is.readLong(), is.readLong());
        SnapshotSerializerId serId = SnapshotSerializerId.of(is.readUTF());
        byte[] bytes = new byte[is.readInt()];
        is.readFully(bytes);
        return Optional.of(new SnapshotData(bytes, serId, lastFact));
      }

      @Override
      byte[] toBytes(SnapshotData sd) {
        ByteArrayDataOutput os = ByteStreams.newDataOutput();
        os.writeShort(MAGIC_BYTES);
        os.writeLong(sd.lastFactId.getMostSignificantBits());
        os.writeLong(sd.lastFactId.getLeastSignificantBits());
        os.writeUTF(sd.snapshotSerializerId.name());
        os.writeInt(sd.serializedProjection.length);
        os.write(sd.serializedProjection);
        return os.toByteArray();
      }
    };

    abstract short magic();

    abstract Optional<SnapshotData> from(ByteArrayDataInput source);

    abstract byte[] toBytes(SnapshotData sd);
  }
}
