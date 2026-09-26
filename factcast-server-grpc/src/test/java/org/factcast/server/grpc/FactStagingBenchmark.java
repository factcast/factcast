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
package org.factcast.server.grpc;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.factcast.core.Fact;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/** Compares the old and new staging paths without network or compression work. */
@State(Scope.Thread)
public class FactStagingBenchmark {

  @Param({"1", "100"})
  public int factsPerBatch;

  private final ProtoConverter converter = new ProtoConverter();
  private List<Fact> facts;

  @Setup
  public void setUp() {
    facts = new ArrayList<>(factsPerBatch);
    for (int i = 0; i < factsPerBatch; i++) {
      facts.add(Fact.builder().ns("benchmark").build("{\"value\":\"äöü " + i + "\"}"));
    }
  }

  @Benchmark
  public int legacy() {
    LegacyStagedFacts staged = new LegacyStagedFacts();
    for (Fact fact : facts) {
      staged.add(fact);
    }
    return converter.createNotificationFor(staged.popAll()).getSerializedSize();
  }

  @Benchmark
  public int exactProtobuf() {
    StagedFacts staged = new StagedFacts(1024 * 1024);
    for (Fact fact : facts) {
      staged.add(converter.toProto(fact));
    }
    return staged.popAll().getSerializedSize();
  }

  private static class LegacyStagedFacts {
    private static final int MAX_BYTES = 1024 * 1024 - (int) (1024 * 1024 * .1);
    private final List<Fact> staged = new ArrayList<>(128);
    private int currentBytes;

    boolean add(Fact fact) {
      int bytes =
          fact.jsonPayload().getBytes(StandardCharsets.UTF_8).length
              + fact.jsonHeader().getBytes(StandardCharsets.UTF_8).length
              + 8;
      if (currentBytes + bytes >= MAX_BYTES) {
        return false;
      }
      staged.add(fact);
      currentBytes += bytes;
      return true;
    }

    List<Fact> popAll() {
      return List.copyOf(staged);
    }
  }
}
