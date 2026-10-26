# Concurrent unconditional publication

Compare direct, synchronous `FactStore.publish(List<Fact>)` throughput between
`0.9.15` and `0.12.5`, using the same PostgreSQL image and database configuration.
The newer release has concurrent publication batching enabled (`max-batch-size=500`).
Schema validation is disabled in both releases, with no schema registry configured.

Use `--versions` to select releases in baseline-first order. Supported tags are
`0.9.15`, `0.11.2`, and `0.12.5`; the default remains `0.9.15 0.12.5`.
`0.11.2` predates concurrent publication batching and runs with batching disabled.

The default comparison runs four cases: each release with **1** and **100** events
per call, using **256 concurrent publishers**. Each case has **60 seconds of warmup**
and **two 60-second measurement iterations**. One complete pass takes about
**12 minutes**, plus source builds, database initialization, and cleanup.

## Run

Requirements: Linux, Java 21, `mvn-lite`, Git, Python 3.12+, Docker access, and
dependency access. Both release tags must exist locally. Run on an otherwise quiet
machine with sufficient disk space for growing PostgreSQL data and archived builds.

From the repository root:

```bash
mkdir -p /tmp/agents
python3 -m venv /tmp/agents/factcast-publish-venv
/tmp/agents/factcast-publish-venv/bin/python -m pip install -r benchmarks/concurrent-publish/requirements.txt
/tmp/agents/factcast-publish-venv/bin/python -B benchmarks/concurrent-publish/run.py
```

For a short check of all four cases before the full comparison:

```bash
/tmp/agents/factcast-publish-venv/bin/python -B benchmarks/concurrent-publish/run.py --smoke
```

Smoke mode caps the workload at eight publishers, two seconds of warmup, and four
seconds of measurement per case. Its reports and charts are labeled **SMOKE ONLY**;
use these results to verify the harness rather than draw performance conclusions.

Options:

```bash
/tmp/agents/factcast-publish-venv/bin/python -B benchmarks/concurrent-publish/run.py \
  --publishers 256 --batch-sizes 1 100 --postgres-image postgres:15 \
  --warmup-seconds 60 --measurement-seconds 120 --repetitions 1
```

Normal comparisons enforce at least 120 measured seconds per case. Measurement
time is divided equally between two iterations. Repeated passes alternate release
order, and reports compare median throughput across passes.

To compare `0.11.2` against `0.12.5` for one event per operation:

```bash
/tmp/agents/factcast-publish-venv/bin/python -B benchmarks/concurrent-publish/run.py \
  --versions 0.11.2 0.12.5 --batch-sizes 1
```

Ratios and report labels use the first selected version as the baseline. A
single-workload run produces one standalone chart panel; selecting all three
supported releases includes all three bars.

## Isolation and measurement

- The runner archives the exact release tags into a unique directory under
  `/tmp/agents` and copies only the shared benchmark sources into those archives.
  It builds each release using `mvn-lite` and the release's own dependencies.
  Maven runs from the original Git working directory with an explicit archived POM
  and standard single-threaded builder, supporting wrappers that require a Git checkout.
  The unrelated `not-central` modules are excluded from the archived reactor build.
- Every version, batch size, and repetition runs in a separate JMH fork with the
  same Java executable and a fixed 1 GiB heap. Coverage and Mockito Java agents
  are absent from measurement JVMs. A dedicated WARN-level logging configuration
  suppresses per-publication test logging. The benchmark is opt-in, outside normal tests.
- Every case initializes a fresh PostgreSQL container and its release-specific
  schema. The PostgreSQL image is resolved to one immutable repository digest
  before any cases run. Containers use disk storage and explicitly enable `fsync`,
  `synchronous_commit`, and `full_page_writes`, overriding Testcontainers' `fsync=off`
  default. Containers are stopped after each case.
  The `0.12.5` publication executor can outlive its Spring context; JMH terminates
  that isolated fork after its shutdown grace period. Verification and container
  shutdown happen first, and this adds cleanup time after measurement.
- Both releases use a 64-connection Tomcat pool, `reWriteBatchedInserts=true`,
  10-second connection timeout, and 60-second socket/query/pool-wait timeouts.
- The public store configuration creates the real Spring-managed store with its
  transaction advice intact. Publishers have no ambient transaction, allowing
  `0.12.5` to use its concurrent publication queue. No gRPC endpoint is involved.
- Each publisher creates unique facts in JMH invocation setup, using its worker
  index and a monotonic counter for IDs. The payload is the same fixed 256-byte
  JSON document. Timed work covers the synchronous publish call and its successful
  acknowledgement counter; event generation and serialization happen outside it.
  JMH reports publications/second; multiplying by application batch size gives
  events/second. Setup still consumes machine resources, so these rates describe
  this harness and host configuration.
- A final SQL count must equal all successfully acknowledged events, including
  warmup. Publication errors, timeouts, count mismatches, validation being enabled,
  or configuration differences fail the comparison. No minimum gain is assumed.

## Results

The runner prints its unique `/tmp/agents/factcast-publish-*` result directory.
It retains:

- `comparison.md` and `comparison.csv`: publication rates, event rates, and ratios
  comparing matching batch sizes.
- `comparison.png` and `comparison.svg`: bar charts with one panel per application
  batch size, consistent release colors, linear axes starting at zero, numeric
  event-rate labels, and throughput-ratio annotations. Panels use separate scales.
- `manifest.json`: commits, image digest/ID, Java version, host information, settings,
  and case status.
- Per-case `jmh.json`, `metadata.json`, and `run.log`: raw measurements and verified
  persistence counts, PostgreSQL version/durability settings, and JVM arguments.
- Source archives expanded for each release, compiled classes, dependency
  classpaths, and build logs, allowing inspection and reproduction.

A failed or incomplete comparison cannot produce a report. To regenerate the
table and charts from an existing successful comparison:

```bash
/tmp/agents/factcast-publish-venv/bin/python -B benchmarks/concurrent-publish/report.py \
  /tmp/agents/factcast-publish-EXAMPLE
```

## Tests

Run the report and runner tests through Maven:

```bash
mvn-lite -f benchmarks/concurrent-publish/pom.xml test \
  -Dbenchmark.python=/tmp/agents/factcast-publish-venv/bin/python
```

`BenchmarkFactsTest` also verifies unique event IDs across 256 publishers and both
batch sizes. It is a normal unit test in `factcast-store`; run it through
`mvn-lite` with the module's usual reactor dependencies available. The benchmark
itself has no JUnit test annotation and is never started by a normal test run.
