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
package org.factcast.factus.serializer.fory;

import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Stopwatch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.factcast.factus.serializer.fory.testjson.Root;
import org.springframework.core.io.ClassPathResource;

public class Benchmark {
  static Root root;
  static final byte[] HUGE_JSON;

  static {
    try {
      HUGE_JSON =
          new ClassPathResource("/huge.json")
              .getContentAsString(StandardCharsets.UTF_8)
              .getBytes(StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    ObjectMapper om =
        new ObjectMapper().enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature());
    try {
      root = om.readValue(HUGE_JSON, Root.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  static final int ITERATIONS = 10000;

  public static void main(String[] args) {
    benchmark("uncompressed", new ForySnapshotSerializer());
    benchmark("compressed", new LZ4ForySnapshotSerializer());
  }

  private static void benchmark(String title, ForySnapshotSerializer underTest) {

    TestProjection source = new TestProjection().root(root);
    underTest.deserialize(TestProjection.class, underTest.serialize(source));

    System.out.println(title + ":");
    System.out.println("Serialized projection size: " + underTest.serialize(source).length);

    Stopwatch started = Stopwatch.createStarted();
    for (int i = 0; i < ITERATIONS; i++) {
      TestProjection target =
          underTest.deserialize(TestProjection.class, underTest.serialize(source));
      assertEquals(source.hashCode(), target.hashCode());
    }
    long ms = started.elapsed(TimeUnit.MILLISECONDS);
    System.out.println("RT " + ms + " ms");
    System.out.println("RT per execution " + (ms / (double) ITERATIONS) + " ms");
  }

  private static void assertEquals(long bar, long foo) {
    if (foo != bar) {
      throw new IllegalStateException();
    }
  }
}
