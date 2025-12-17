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
package org.factcast.test;

import com.google.common.io.ByteSource;
import com.google.common.io.ByteStreams;
import io.grpc.Codec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.Assertions;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

@Slf4j
@SuppressWarnings("java:S112")
@Disabled("should only be run manually")
public abstract class AbstractCompressionPerformanceTest {
  private static final int MAX_FACTS = 1000000;

  private static byte[] bytes;
  private static byte[] decompressedBytes;

  @BeforeAll
  static void setup() {
    ArrayList<Fact> facts = new ArrayList<>(MAX_FACTS);
    for (int i = 0; i < MAX_FACTS; i++) {
      facts.add(Fact.builder().ns("ns").type("type").buildWithoutPayload());
    }
    bytes = FactCastJson.writeValueAsString(facts).getBytes(StandardCharsets.UTF_8);
    decompressedBytes = new byte[bytes.length];
    log.info("KBytes to be written: " + bytes.length / 1024);
  }

  @SneakyThrows
  @Test
  void testPerformance() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    measure(
        "compress",
        () -> {
          OutputStream os = getCodecToTest().compress(baos);
          os.write(bytes);
          os.close();
        });

    byte[] compressedBytes = baos.toByteArray();

    measure(
        "decompress",
        () -> {
          InputStream is = ByteSource.wrap(compressedBytes).openStream();
          is = getCodecToTest().decompress(is);

          decompressedBytes = ByteStreams.toByteArray(is);
        });

    Assertions.assertThat(decompressedBytes).isEqualTo(bytes);
  }

  protected abstract Codec getCodecToTest();

  @SneakyThrows
  protected void measure(String title, ThrowingRunnable r) {
    long start = System.currentTimeMillis();
    r.run();
    long end = System.currentTimeMillis();
    LoggerFactory.getLogger(getClass()).info("{}: {}ms", title, (end - start));
  }

  interface ThrowingRunnable {
    void run() throws Exception;
  }
}
