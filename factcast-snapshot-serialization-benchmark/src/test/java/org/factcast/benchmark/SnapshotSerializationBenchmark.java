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
package org.factcast.benchmark;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.factcast.core.snap.local.InMemorySnapshotCache;
import org.factcast.core.snap.local.InMemorySnapshotProperties;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.serializer.JacksonSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.serializer.binary.BinaryJacksonSnapshotSerializerCustomizer;
import org.factcast.factus.serializer.binary.CompressedBinaryJacksonSnapshotSerializer;
import org.factcast.factus.serializer.binary.UncompressedBinaryJacksonSnapshotSerializer;
import org.factcast.factus.serializer.fury.FurySnapshotSerializer;
import org.factcast.factus.serializer.fury.LZ4FurySnapshotSerializer;
import org.factcast.factus.serializer.fury.SnappyFurySnapshotSerializer;
import org.factcast.factus.snapshot.SnapshotData;
import org.factcast.factus.snapshot.SnapshotIdentifier;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Fork(1)
@Threads(1)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
public class SnapshotSerializationBenchmark {

  @State(Scope.Thread)
  public static class SnapshotState {
    @Param({"jackson", "binary", "binary-lz4", "fury", "fury-lz4", "fury-snappy"})
    public String serializerName;

    @Param({"16", "1024"})
    public int entryCount;

    SnapshotSerializer serializer;
    InMemorySnapshotCache cache;
    SnapshotIdentifier id;
    SampleProjection projection;
    UUID lastFactId;

    @Setup(Level.Trial)
    public void setUp() {
      serializer = serializerFor(serializerName);
      cache = new InMemorySnapshotCache(new InMemorySnapshotProperties());
      id = SnapshotIdentifier.of(SampleProjection.class);
      lastFactId = UUID.fromString("00000000-0000-0000-0000-000000000001");
      projection = new SampleProjection(entryCount);

      byte[] bytes = serializer.serialize(projection);
      cache.store(id, new SnapshotData(bytes, serializer.id(), lastFactId));
      SampleProjection restored = serializer.deserialize(SampleProjection.class, bytes);
      if (!projection.sameContents(restored)) {
        throw new IllegalStateException("Snapshot round trip failed for " + serializerName);
      }
      System.out.printf(
          "%s, %d entries: %d serialized bytes%n", serializerName, entryCount, bytes.length);
    }

    private static SnapshotSerializer serializerFor(String name) {
      switch (name) {
        case "jackson":
          return new JacksonSnapshotSerializer();
        case "binary":
          return new UncompressedBinaryJacksonSnapshotSerializer(
              BinaryJacksonSnapshotSerializerCustomizer.defaultCustomizer());
        case "binary-lz4":
          return new CompressedBinaryJacksonSnapshotSerializer(
              BinaryJacksonSnapshotSerializerCustomizer.defaultCustomizer());
        case "fury":
          return new FurySnapshotSerializer();
        case "fury-lz4":
          return new LZ4FurySnapshotSerializer();
        case "fury-snappy":
          return new SnappyFurySnapshotSerializer();
        default:
          throw new IllegalArgumentException("Unknown serializer: " + name);
      }
    }
  }

  @Benchmark
  public void serializeAndStore(SnapshotState state) {
    byte[] bytes = state.serializer.serialize(state.projection);
    state.cache.store(state.id, new SnapshotData(bytes, state.serializer.id(), state.lastFactId));
  }

  @Benchmark
  public SampleProjection findAndDeserialize(SnapshotState state) {
    SnapshotData data = state.cache.find(state.id).orElseThrow(IllegalStateException::new);
    return state.serializer.deserialize(SampleProjection.class, data.serializedProjection());
  }

  public static class SampleProjection implements SnapshotProjection {
    @JsonProperty public List<SampleEntry> entries = new ArrayList<>();

    public SampleProjection() {}

    SampleProjection(int count) {
      for (int i = 0; i < count; i++) {
        entries.add(new SampleEntry(i, "customer-" + i, "region-" + (i % 8)));
      }
    }

    boolean sameContents(SampleProjection other) {
      if (other == null || entries.size() != other.entries.size()) {
        return false;
      }
      for (int i = 0; i < entries.size(); i++) {
        SampleEntry expected = entries.get(i);
        SampleEntry actual = other.entries.get(i);
        if (expected.id != actual.id
            || !expected.customer.equals(actual.customer)
            || !expected.region.equals(actual.region)) {
          return false;
        }
      }
      return true;
    }
  }

  public static class SampleEntry {
    @JsonProperty public int id;
    @JsonProperty public String customer;
    @JsonProperty public String region;

    public SampleEntry() {}

    SampleEntry(int id, String customer, String region) {
      this.id = id;
      this.customer = customer;
      this.region = region;
    }
  }
}
