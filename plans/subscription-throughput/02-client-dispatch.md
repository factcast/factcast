# Client notification dispatch

Status: implemented.

## Goal

Reduce per-notification scheduling overhead while preserving ordered, synchronous delivery and backpressure.

## Implementation

1. Measure the cost of `ClientStreamObserver.onNext` submitting every notification to its single-thread executor and waiting for the result.
2. Process notifications directly in the receiving callback. Keep processing synchronous so a slow observer still slows receipt of further notifications.
3. Add one terminal-state guard shared by completion, error, keepalive expiry, and cancellation. Keep exception translation and callback order.

## Verification

- Compare client-observed facts per second, callback latency, CPU, and allocations before and after.
- Test batch ordering, slow observers, observer exceptions, close during delivery, keepalive expiry, and reconnect.
- Retain direct dispatch only if throughput improves repeatably without a material callback-latency regression.

## Execution result

`ClientStreamObserver` now processes notifications on the receiving thread. A delivery lock keeps fact callbacks and terminal callbacks ordered, while subscription close stops any remaining facts in a batch. Keepalive expiry checks the latest activity after an in-progress callback finishes.

The focused `ClientStreamObserverTest` and `ResilientGrpcSubscriptionTest` suites pass (57 tests). They cover callback thread, synchronous backpressure, batch cancellation, terminal ordering, observer errors, keepalive, and reconnect.

The `ClientDispatchBenchmark` JMH fixture measures client callback delivery with a no-op observer and no network, server, database, or transformation work. Using identical settings and fixtures against the original implementation and this change:

| Facts per notification | Original | Direct dispatch | Change |
| --- | ---: | ---: | ---: |
| 1 | 113,953 notifications/s | 2,631,758 notifications/s | 23.1× |
| 100 | 23,029 notifications/s | 29,495 notifications/s | 1.28× |

Both results used 3 warmup iterations and 5 measurement iterations of 2 seconds each. These are isolated throughput measurements; client-observed end-to-end throughput, callback latency, CPU, and allocations remain to be measured in a deployed subscription. The assumed 10% transformation ratio and 95% transformation cache hit rate do not affect this isolated client-dispatch benchmark.
