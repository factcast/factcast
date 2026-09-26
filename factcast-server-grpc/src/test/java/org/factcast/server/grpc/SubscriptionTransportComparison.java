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

import io.grpc.*;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.factcast.core.Fact;
import org.factcast.grpc.api.CompressionCodecs;
import org.factcast.grpc.api.GrpcConstants;
import org.factcast.grpc.api.Headers;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.*;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;
import org.factcast.grpc.lz4.Lz4GrpcCodec;
import org.factcast.grpc.snappy.SnappycGrpcCodec;

/**
 * Loopback gRPC comparison of batch targets and negotiated response codecs. Run the main class with
 * the server module's test classpath; no database is required.
 */
public final class SubscriptionTransportComparison {

  private static final int CLIENT_LIMIT = GrpcConstants.DEFAULT_CLIENT_INBOUND_MESSAGE_SIZE;
  private static final int FACT_COUNT = 4096;
  private static final int REQUESTS_PER_ROUND = 5;
  private static final int ROUNDS = 3;
  private static final MSG_SubscriptionRequest REQUEST =
      MSG_SubscriptionRequest.getDefaultInstance();

  private SubscriptionTransportComparison() {}

  public static void main(String[] args) throws Exception {
    CompressorRegistry compressors = CompressorRegistry.getDefaultInstance();
    compressors.register(new Lz4GrpcCodec());
    compressors.register(new SnappycGrpcCodec());
    DecompressorRegistry decompressors =
        DecompressorRegistry.getDefaultInstance()
            .with(new Lz4GrpcCodec(), true)
            .with(new SnappycGrpcCodec(), true);
    List<String> codecs = new ArrayList<>();
    codecs.add("identity");
    for (String codec : new CompressionCodecs(compressors).available().split(",")) {
      if (decompressors.lookupDecompressor(codec) != null) {
        codecs.add(codec);
      }
    }
    boolean reverse = args.length > 0 && args[0].equals("reverse");
    boolean compressionOnly = Arrays.asList(args).contains("compression-only");
    if (reverse) {
      Collections.reverse(codecs);
    }

    List<MSG_Fact> facts = facts();
    System.out.printf(
        Locale.ROOT, "facts=%d clientLimit=%d codecs=%s%n", facts.size(), CLIENT_LIMIT, codecs);
    double[] targets = reverse ? new double[] {0.90, 0.50, 0.25} : new double[] {0.25, 0.50, 0.90};
    for (double targetFraction : targets) {
      List<MSG_Notification> notifications = pack(facts, (int) (targetFraction * 100));
      int rawBytes = notifications.stream().mapToInt(MSG_Notification::getSerializedSize).sum();
      int maxNotification =
          notifications.stream().mapToInt(MSG_Notification::getSerializedSize).max().orElseThrow();
      for (String codec : codecs) {
        CompressionResult compressed = compress(notifications, compressors.lookupCompressor(codec));
        if (compressionOnly) {
          System.out.printf(
              Locale.ROOT,
              "target=%.2f codec=%s notifications=%d rawBytes=%d wireBytes=%d encodeCpuMs=%.3f%n",
              targetFraction,
              codec,
              notifications.size(),
              rawBytes,
              compressed.bytes(),
              compressed.cpuNanos() / 1_000_000.0);
          continue;
        }
        Result result = transport(notifications, codec, compressors, decompressors);
        System.out.printf(
            Locale.ROOT,
            "target=%.2f codec=%s notifications=%d maxBytes=%d rawBytes=%d wireBytes=%d "
                + "encodeCpuMs=%.3f factsPerSecond=%.0f rounds=%s p95RequestMs=%.3f "
                + "readinessWaitMs=%.3f%n",
            targetFraction,
            codec,
            notifications.size(),
            maxNotification,
            rawBytes,
            compressed.bytes(),
            compressed.cpuNanos() / 1_000_000.0,
            result.factsPerSecond(),
            result.rounds(),
            result.p95RequestNanos() / 1_000_000.0,
            result.readinessWaitNanos() / 1_000_000.0);
      }
    }
  }

  private static List<MSG_Fact> facts() {
    List<MSG_Fact> facts = new ArrayList<>(FACT_COUNT);
    Random random = new Random(427);
    for (int i = 0; i < FACT_COUNT; i++) {
      byte[] content = new byte[i % 10 == 0 ? 2048 : 1024];
      random.nextBytes(content);
      String payload = "{\"value\":\"" + Base64.getEncoder().encodeToString(content) + "\"}";
      String header =
          "{\"ns\":\"transport\",\"id\":\""
              + UUID.nameUUIDFromBytes(("fact-" + i).getBytes(StandardCharsets.UTF_8))
              + "\"}";
      facts.add(MSG_Fact.newBuilder().setHeader(header).setPayload(payload).build());
    }
    return facts;
  }

  private static List<MSG_Notification> pack(List<MSG_Fact> facts, int targetPercent) {
    ProtoConverter converter = new ProtoConverter();
    StagedFacts staged = new StagedFacts(CLIENT_LIMIT, targetPercent);
    List<MSG_Notification> notifications = new ArrayList<>();
    for (MSG_Fact fact : facts) {
      Fact coreFact = Fact.of(fact.getHeader(), fact.getPayload());
      if (!staged.add(coreFact)) {
        notifications.add(converter.createNotificationFor(staged.popAll()));
        if (!staged.add(coreFact)) {
          throw new IllegalStateException("fact cannot fit an empty batch");
        }
      }
    }
    if (!staged.isEmpty()) {
      notifications.add(converter.createNotificationFor(staged.popAll()));
    }
    if (notifications.stream().anyMatch(n -> n.getSerializedSize() > CLIENT_LIMIT)) {
      throw new IllegalStateException("notification exceeded client inbound limit");
    }
    return notifications;
  }

  private static CompressionResult compress(
      List<MSG_Notification> notifications, Compressor compressor) throws Exception {
    ThreadMXBean bean = ManagementFactory.getThreadMXBean();
    long[] samples = new long[5];
    long bytes = 0;
    for (int iteration = 0; iteration < 8; iteration++) {
      bytes = 0;
      long start = bean.getCurrentThreadCpuTime();
      for (MSG_Notification notification : notifications) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (compressor == null || compressor.getMessageEncoding().equals("identity")) {
          notification.writeTo(output);
        } else {
          try (var compressed = compressor.compress(output)) {
            notification.writeTo(compressed);
          }
        }
        bytes += output.size() + 5L;
      }
      if (iteration >= 3) {
        samples[iteration - 3] = bean.getCurrentThreadCpuTime() - start;
      }
    }
    Arrays.sort(samples);
    return new CompressionResult(bytes, samples[2]);
  }

  private static Result transport(
      List<MSG_Notification> notifications,
      String codec,
      CompressorRegistry compressors,
      DecompressorRegistry decompressors)
      throws Exception {
    AtomicLong blockedNanos = new AtomicLong();
    RemoteFactStoreGrpc.RemoteFactStoreImplBase service =
        new RemoteFactStoreGrpc.RemoteFactStoreImplBase() {
          @Override
          public void subscribe(
              MSG_SubscriptionRequest request, StreamObserver<MSG_Notification> responseObserver) {
            ServerCallStreamObserver<MSG_Notification> call =
                (ServerCallStreamObserver<MSG_Notification>) responseObserver;
            call.setMessageCompression(!codec.equals("identity"));
            BlockingStreamObserver<MSG_Notification> stream =
                new BlockingStreamObserver<>("transport-benchmark", call) {
                  @Override
                  void waitForDelegate() {
                    long start = System.nanoTime();
                    try {
                      super.waitForDelegate();
                    } finally {
                      blockedNanos.addAndGet(System.nanoTime() - start);
                    }
                  }
                };
            Thread sender =
                new Thread(
                    () -> {
                      try {
                        for (MSG_Notification notification : notifications) {
                          stream.onNext(notification);
                        }
                        stream.onCompleted();
                      } catch (Throwable error) {
                        stream.onError(error);
                      }
                    },
                    "transport-benchmark-sender");
            sender.setDaemon(true);
            sender.start();
          }
        };
    Server server =
        ServerBuilder.forPort(0)
            .compressorRegistry(compressors)
            .decompressorRegistry(decompressors)
            .addService(service)
            .intercept(new GrpcCompressionInterceptor(new CompressionCodecs(compressors)))
            .build()
            .start();
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress("127.0.0.1", server.getPort())
            .usePlaintext()
            .compressorRegistry(compressors)
            .decompressorRegistry(decompressors)
            .build();
    try {
      Metadata metadata = new Metadata();
      if (!codec.equals("identity")) {
        metadata.put(Headers.MESSAGE_COMPRESSION, codec);
      }
      RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub =
          RemoteFactStoreGrpc.newBlockingStub(channel)
              .withMaxInboundMessageSize(CLIENT_LIMIT)
              .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
      if (!codec.equals("identity")) {
        stub = stub.withCompression(codec);
      }
      for (int i = 0; i < 10; i++) {
        receive(stub);
      }
      List<Double> rates = new ArrayList<>(ROUNDS);
      List<Long> requestNanos = new ArrayList<>(ROUNDS * REQUESTS_PER_ROUND);
      for (int round = 0; round < ROUNDS; round++) {
        long start = System.nanoTime();
        for (int request = 0; request < REQUESTS_PER_ROUND; request++) {
          long requestStart = System.nanoTime();
          receive(stub);
          requestNanos.add(System.nanoTime() - requestStart);
        }
        long elapsed = System.nanoTime() - start;
        rates.add((double) FACT_COUNT * REQUESTS_PER_ROUND * 1_000_000_000 / elapsed);
      }
      requestNanos.sort(Long::compareTo);
      long p95 = requestNanos.get((int) Math.ceil(requestNanos.size() * .95) - 1);
      return new Result(
          rates.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
          rates,
          p95,
          blockedNanos.get());
    } finally {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private static void receive(RemoteFactStoreGrpc.RemoteFactStoreBlockingStub stub) {
    ProtoConverter converter = new ProtoConverter();
    Iterator<MSG_Notification> stream = stub.subscribe(REQUEST);
    int received = 0;
    while (stream.hasNext()) {
      received += converter.fromProto(stream.next().getFacts()).size();
    }
    if (received != FACT_COUNT) {
      throw new IllegalStateException("received " + received + " of " + FACT_COUNT + " facts");
    }
  }

  private record Result(
      double factsPerSecond, List<Double> rounds, long p95RequestNanos, long readinessWaitNanos) {}

  private record CompressionResult(long bytes, long cpuNanos) {}
}
