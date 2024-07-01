/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.itests.factus.client;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPOutputStream;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;
import org.factcast.grpc.lz4.Lz4GrpcClientCodec;
import org.factcast.grpc.snappy.SnappycGrpcClientCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext;

@Slf4j
class X {
  private static final int MAX_FACTS = 100000;

  private static ArrayList<Fact> facts;
  private static byte[] bytes;

  @BeforeAll
  static void setup() {
    facts = new ArrayList<>(MAX_FACTS);
    for (int i = 0; i < MAX_FACTS; i++) {
      facts.add(Fact.builder().ns("ns").type("type").buildWithoutPayload());
    }
    bytes = FactCastJson.writeValueAsString(facts).getBytes(StandardCharsets.UTF_8);
    System.out.println("KBytes to be written: " + bytes.length / 1024);
  }

  //  @SneakyThrows
  //  @DirtiesContext
  //  @Test
  //  void testLz4() {
  //    measure(
  //        "lz4",
  //        () -> {
  //          try {
  //            OutputStream os = new ByteArrayOutputStream(bytes.length / 4);
  //            os = new Lz4cGrpcClientCodec().compress(os);
  //            os.write(bytes);
  //            os.flush();
  //          } catch (Exception e) {
  //            throw new RuntimeException(e);
  //          }
  //        });
  //  }

  @SneakyThrows
  @DirtiesContext
  @Test
  void testZstd() {
    measure(
        "zstd",
        () -> {
          try {
            OutputStream os = new ByteArrayOutputStream(bytes.length / 4);
            os = new ZstdCompressorOutputStream(os);
            os.write(bytes);
            os.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @SneakyThrows
  @DirtiesContext
  @Test
  void testNpLz4() {
    measure(
        "np-lz4",
        () -> {
          try {
            OutputStream os = new ByteArrayOutputStream(bytes.length / 4);
            os = new Lz4GrpcClientCodec().compress(os);
            os.write(bytes);
            os.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @SneakyThrows
  @DirtiesContext
  @Test
  void testNone() {
    measure(
        "none",
        () -> {
          try {
            OutputStream os = new ByteArrayOutputStream(bytes.length / 4);
            os.write(bytes);
            os.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @SneakyThrows
  @DirtiesContext
  @Test
  void testGzip() {
    measure(
        "gz",
        () -> {
          try {
            OutputStream os = new ByteArrayOutputStream(bytes.length / 4);
            os = new GZIPOutputStream(os);
            os.write(bytes);
            os.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  @SneakyThrows
  @DirtiesContext
  @Test
  void testSnap() {
    measure(
        "snappy",
        () -> {
          try {
            OutputStream os = new ByteArrayOutputStream(bytes.length / 4);
            os = new SnappycGrpcClientCodec().compress(os);
            os.write(bytes);
            os.flush();
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  private void measure(String name, Runnable r) {
    long start = System.currentTimeMillis();
    r.run();
    long end = System.currentTimeMillis();
    System.out.println(name + ":" + (end - start) + "ms");
  }
}
