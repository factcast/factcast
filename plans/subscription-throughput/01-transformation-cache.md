# Transformation cache path

## Goal

Improve client-observed catch-up throughput when 10% of facts require transformation and 95% of those requests hit the transformation cache. Exclude the initial catch-up database query.

## Implementation

1. Measure transformation batch count and size, batched cache lookup time, cache hits and misses, and transformation time.
2. Return batched cache results keyed by fact ID, target version, and transformation path in both cache implementations. Preserve existing hit and miss counters.
3. Resolve all-hit batches on the subscription thread. For mixed batches, run only cache misses through the transformation pool and restore the original request order. Consume each transformation request once.
4. Keep the existing 100-signal buffer size and wire interface.

## Verification

- Test all-hit, mixed, and all-miss batches; repeated fact IDs with different transformation keys; ordering; errors; and request consumption.
- Benchmark with the stated ratio and hit rate, excluding the initial catch-up query. Compare client-observed facts per second, CPU, allocation, and batch latency against the prior implementation.
- Retain the change only if throughput improves repeatably without a material memory or delivery-latency regression.

## Status

Implementation, focused unit tests, and an isolated JMH fixture for a 200-fact window are complete. With trace logging disabled in both runs, the fixture measured 24,662 batches/s at `HEAD` and 295,235 batches/s with this change (three one-second measurement iterations each). This isolates transformation-service overhead with an in-memory fixture; it does not establish an end-to-end client throughput gain. A client-throughput comparison remains to be run before rollout.
