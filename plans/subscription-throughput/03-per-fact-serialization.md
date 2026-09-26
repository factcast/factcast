# Per-fact serialization

Status: implemented.

## Goal

Remove repeated work for every delivered fact and make batch limits exact.

## Implementation

1. Use `PgFact.id()` for blacklist membership instead of parsing the JSON header.
2. Build each protobuf fact once when staging it. Count its encoded size plus notification overhead when deciding whether it fits a batch.
3. Preserve the client-advertised inbound limit and compression negotiation. Send a fact alone if it exceeds the normal batch target but fits the inbound limit; fail explicitly if it cannot fit.

## Verification

- Compare CPU, allocation, and client-observed throughput against the current staging path.
- Test multibyte JSON, batch boundaries, oversized facts, and exact delivery counts with no loss or duplication.

## Execution result

The blacklist filter now reads the already available `PgFact.id()` field. The gRPC adapter converts each fact to `MSG_Fact` once and stages that message. `StagedFacts` counts protobuf field tags, length prefixes, and the complete notification envelope, so the byte counter equals `MSG_Notification.getSerializedSize()`. It retains the 90% normal batch target, permits one fact above that target when it fits the client's inbound limit, and raises gRPC `RESOURCE_EXHAUSTED` when a single notification cannot fit. The `BYTES_SENT` metric now records the exact protobuf notification size.

The isolated `FactStagingBenchmark` compares the old UTF-8 sizing and flush conversion with the new staging path for 100 facts per batch. With 3 warmup and 5 measurement iterations of 2 seconds each, the new path completed 117,388 batches/s versus 80,427 batches/s (1.46×), and allocated 5,152 versus 29,832 bytes per batch (83% less). The benchmark excludes the database, transformations, compression, network, and client callbacks. Client-observed throughput and CPU in a deployed subscription remain to be measured; the 10% transformation ratio and 95% cache hit rate do not affect this isolated comparison.

Focused tests cover multibyte JSON, exact protobuf sizes and batch boundaries, standalone oversized facts, hard-limit rejection, delivery order and count, metrics, and blacklist lookup without header parsing. The 56 `FactStoreGrpcServiceTest` service-level tests also pass.
