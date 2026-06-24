/*
 * Copyright © 2017-2025 factcast.org
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
package org.factcast.store.registry.transformation.cache.bench;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.*;

/**
 * Manual entry point for the transformationcache benchmark. The class name intentionally does NOT
 * end in {@code Test}, so Surefire ignores it on a normal build; run it explicitly:
 *
 * <pre>
 *   # just the size comparison (fast):
 *   mvn -o -pl factcast-store test -Dtest=BenchmarkRunner#sizes
 *
 *   # full JMH run (slow), optionally tuned:
 *   mvn -o -pl factcast-store test -Dtest=BenchmarkRunner#benchmark \
 *       -Dbench.params=1,100,500 -Dbench.forks=1 -Dbench.wi=3 -Dbench.mi=5 -Dbench.time=2
 * </pre>
 *
 * Connection/CSV overrides from {@link BenchSchema} apply (e.g. {@code -Dpg.url=...}).
 */
class BenchmarkRunner {

  @Test
  void sizes() throws Exception {
    SizeReport.main(new String[0]);
  }

  @Test
  void benchmark() throws Exception {
    int wi = Integer.getInteger("bench.wi", 3);
    int mi = Integer.getInteger("bench.mi", 5);
    int forks = Integer.getInteger("bench.forks", 1);
    int time = Integer.getInteger("bench.time", 2);
    String params = System.getProperty("bench.params", "1,100,500");

    Options opt =
        new OptionsBuilder()
            .include(TransformationCacheBenchmark.class.getSimpleName())
            .param("batchSize", params.split(","))
            .warmupIterations(wi)
            .warmupTime(TimeValue.seconds(time))
            .measurementIterations(mi)
            .measurementTime(TimeValue.seconds(time))
            .forks(forks)
            // replace inherited args so the fork is NOT instrumented by jacoco/mockito agents
            .jvmArgs("-Xms1g", "-Xmx1g")
            .shouldFailOnError(true)
            .resultFormat(ResultFormatType.TEXT)
            .result("target/bench-results.txt")
            .build();
    new Runner(opt).run();
  }
}
