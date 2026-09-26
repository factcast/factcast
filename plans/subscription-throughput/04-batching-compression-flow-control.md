# Batching, compression, and flow control

Status: implemented and measured on a synthetic loopback stream.

## Goal

Choose a notification target and codec that maximize delivered facts per second within the client message limit.

## Implementation

1. Measure notification count and size, compressed bytes, compression CPU, and time blocked by gRPC readiness.
2. Benchmark several batch targets below the advertised inbound limit with every mutually available codec using the same fact set.
3. If a smaller target wins consistently, add a server-side target-size property with the current behavior as its default. Continue to block on readiness and preserve ordering.

## Verification

- Select the configuration with the highest repeatable client-observed throughput that stays within the message limit and does not materially worsen memory use or delivery latency.
- Test slow clients, cancellation, ordered delivery, and catch-up completion at the selected target.

## Execution result

`factcast.grpc.batch-target-percent` now sets the server's normal batch target as a percentage of the client-advertised inbound limit. Valid values are 1–90; the default is 90, preserving the existing behavior. A fact larger than the chosen target is sent by itself when its notification fits the client limit, or fails with gRPC `RESOURCE_EXHAUSTED` when it cannot fit. The server still uses `BlockingStreamObserver` for readiness backpressure, and codec negotiation is unchanged.

`SubscriptionTransportComparison` sends the same 4,096 facts through a loopback gRPC server and decodes them at the client. Ten requests warm each configuration, followed by three rounds of five measured requests. It compares 25%, 50%, and 90% targets with every codec available to both endpoints: identity, lz4, snappyc, and gzip. The fact set has 1 KiB source payloads, with 10% enlarged to 2 KiB to represent transformed output; its bytes are deterministic and mostly incompressible. The 95% transformation cache hit rate affects upstream work and is outside this transport comparison.

| Target | Notifications | Largest notification | Identity facts/s, forward / reverse | LZ4 facts/s, forward / reverse |
| --- | ---: | ---: | ---: | ---: |
| 25% | 8 | 0.92 MB | 219k / 445k | 237k / 309k |
| 50% | 4 | 1.84 MB | 374k / 363k | 284k / 262k |
| 90% | 2 | 3.31 MB | 316k / 302k | 213k / 211k |

The 25% result depends strongly on configuration order, so it is not selected. The 50% target beat the 90% target in both orders for identity and lz4, reduced the largest message by 44%, and did not worsen measured p95 request latency for those codecs. The tested stream can therefore be trialed with `factcast.grpc.batch-target-percent=50`; 90% remains the production default until representative deployment measurements confirm a change. All notifications stayed below the client inbound limit, and every request delivered exactly 4,096 facts.

Across the whole stream at 50%, identity used 6.50 MB of protobuf plus gRPC message frames, lz4 6.38 MB, snappyc 6.35 MB, and gzip 4.81 MB. Median encode CPU for the whole stream, including protobuf serialization, was about 6 ms, 8 ms, 150 ms, and 160 ms respectively. Identity had the highest client receipt rate for this fact set. Codec defaults remain unchanged because compression results depend on payload compressibility and network conditions. Readiness wait time was recorded for every configuration; the server continued blocking on unready channels.

Focused tests cover slow clients, cancellation, ordering, catch-up completion, server setting propagation, valid target bounds, and oversized facts. The comparison is reproducible from the server module's test classpath by running `org.factcast.server.grpc.SubscriptionTransportComparison` (append `reverse` to invert configuration order or `compression-only` for the offline byte and CPU comparison). These loopback results do not measure database catch-up, production network bandwidth, or deployed client throughput.
