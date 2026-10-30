# factcast-store-benchmark

Benchmarks for the PostgreSQL based `factcast-store` implementation.

The [concurrent publication benchmark](concurrent-publish/README.md) compares
`FactStore.publish` throughput between releases and generates tables and bar charts.
Its README covers requirements, setup, execution, and results.

This module participates in the root Maven build through the `not-central` profile.
Its Python tests run during the Maven `test` phase; performance measurements remain
opt-in. Maven creates an isolated virtual environment under `/tmp/agents` and installs
the declared Python requirements. Python 3.12+ with `venv` and dependency access is
required. Run the tests from the repository root:

```bash
mvn-lite -pl factcast-store-benchmark test
```

Use `-DskipUnitTests=true` or `-DskipTests` to skip the Python tests.
Both flags also skip Python environment setup and dependency installation.
Use `-Dbenchmark.python=/path/to/python` to select the Python interpreter and
`-Dbenchmark.venv=/tmp/agents/custom-benchmark-venv` to change the environment path.
