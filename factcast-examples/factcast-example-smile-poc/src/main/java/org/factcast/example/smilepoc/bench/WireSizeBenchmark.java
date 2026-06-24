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
package org.factcast.example.smilepoc.bench;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Codec;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.factcast.example.smilepoc.PocProperties;
import org.factcast.example.smilepoc.loader.SampleLoader;
import org.factcast.example.smilepoc.report.Measurement;
import org.factcast.example.smilepoc.report.ReportWriter;
import org.factcast.grpc.lz4.Lz4GrpcCodec;
import org.factcast.grpc.snappy.SnappycGrpcCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * Experiment 2 — does SMILE actually shrink the wire once the production compressor runs?
 *
 * <p>For each sampled fact, builds the wire payload as JSON (header+payload UTF-8, as in today's
 * {@code MSG_Fact}) and as SMILE, then compresses each per-message with the exact codecs the gRPC
 * channel negotiates ({@link Lz4GrpcCodec}, {@link SnappycGrpcCodec}, gRPC's built-in gzip — order
 * lz4 → snappyc → snappy → gzip, so lz4 is the real-world default). Reports raw and compressed
 * bytes plus the smile/json ratio per codec.
 */
@Slf4j
@Component
public class WireSizeBenchmark {

  private final ObjectMapper jsonMapper;
  private final ObjectMapper smileMapper;
  private final SampleLoader samples;
  private final PocProperties props;
  private final ReportWriter report;

  public WireSizeBenchmark(
      ObjectMapper jsonMapper,
      @Qualifier("smileMapper") ObjectMapper smileMapper,
      SampleLoader samples,
      PocProperties props,
      ReportWriter report) {
    this.jsonMapper = jsonMapper;
    this.smileMapper = smileMapper;
    this.samples = samples;
    this.props = props;
    this.report = report;
  }

  private record NamedCodec(String name, Codec codec) {}

  public void run() throws Exception {
    int sampleSize = Math.max(1, props.bench().wireSizeSample());
    List<SampleLoader.Sample> data = samples.load(sampleSize);
    int n = data.size();
    log.info("Wire size: compressing {} facts with lz4 / snappyc / gzip", n);

    List<NamedCodec> codecs =
        List.of(
            new NamedCodec("lz4", new Lz4GrpcCodec()),
            new NamedCodec("snappyc", new SnappycGrpcCodec()),
            new NamedCodec("gzip", new Codec.Gzip()));

    long jsonRaw = 0;
    long smileRaw = 0;
    long[] jsonComp = new long[codecs.size()];
    long[] smileComp = new long[codecs.size()];

    for (SampleLoader.Sample s : data) {
      byte[] jsonBytes = concat(utf8(s.header()), utf8(s.payload()));
      byte[] smileBytes =
          concat(
              smileMapper.writeValueAsBytes(jsonMapper.readTree(s.header())),
              smileMapper.writeValueAsBytes(jsonMapper.readTree(s.payload())));
      jsonRaw += jsonBytes.length;
      smileRaw += smileBytes.length;
      for (int c = 0; c < codecs.size(); c++) {
        jsonComp[c] += compressedSize(codecs.get(c).codec(), jsonBytes);
        smileComp[c] += compressedSize(codecs.get(c).codec(), smileBytes);
      }
    }

    List<String[]> table = new ArrayList<>();
    table.add(row("raw", jsonRaw, smileRaw, n));
    emit("wire_raw", jsonRaw, smileRaw, n);
    for (int c = 0; c < codecs.size(); c++) {
      String name = codecs.get(c).name();
      table.add(row(name, jsonComp[c], smileComp[c], n));
      emit("wire_" + name, jsonComp[c], smileComp[c], n);
    }

    StringBuilder sb = new StringBuilder("\n==== wire size (avg bytes/fact, n=" + n + ") ====\n");
    sb.append(String.format("%-10s %12s %12s %12s%n", "codec", "json", "smile", "smile/json"));
    for (String[] r : table) {
      sb.append(String.format("%-10s %12s %12s %12s%n", r[0], r[1], r[2], r[3]));
    }
    log.info(sb.toString());
  }

  private void emit(String benchmark, long jsonTotal, long smileTotal, int n) {
    report.add(
        new Measurement(
            "json",
            benchmark,
            n,
            0,
            0,
            0,
            0,
            0,
            String.format("avg=%dB/fact total=%.2fMB", jsonTotal / n, jsonTotal / 1e6)));
    report.add(
        new Measurement(
            "smile",
            benchmark,
            n,
            0,
            0,
            0,
            0,
            0,
            String.format(
                "avg=%dB/fact total=%.2fMB ratio=%.2f",
                smileTotal / n, smileTotal / 1e6, (double) smileTotal / jsonTotal)));
  }

  private static String[] row(String codec, long jsonTotal, long smileTotal, int n) {
    return new String[] {
      codec,
      Long.toString(jsonTotal / n),
      Long.toString(smileTotal / n),
      String.format("%.2f", (double) smileTotal / jsonTotal)
    };
  }

  private static int compressedSize(Codec codec, byte[] data) throws Exception {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    try (OutputStream cos = codec.compress(bos)) {
      cos.write(data);
    }
    return bos.size();
  }

  private static byte[] utf8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] concat(byte[] a, byte[] b) {
    byte[] out = new byte[a.length + b.length];
    System.arraycopy(a, 0, out, 0, a.length);
    System.arraycopy(b, 0, out, a.length, b.length);
    return out;
  }
}
