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
package org.factcast.store.internal.filter;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import lombok.*;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.PgFact;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.springframework.core.io.ClassPathResource;

@State(Scope.Benchmark)
public class AggIdPropertyMatcherBenchmark {

  private String json;
  private @NonNull UUID id = UUID.fromString("c648d357-639a-11f0-a1d7-0278bb6dd5f3");
  private PgFact testFact;
  private AggIdPropertyMatcher parsing;

  @SneakyThrows
  @Setup(Level.Iteration)
  public void readJson() {
    json = new ClassPathResource("/sample.json").getContentAsString(StandardCharsets.UTF_8);
    JsonNode tree = FactCastJson.readTree(json);
    testFact = PgFact.of(tree.path("header"), tree.path("payload"));
    FactSpec spec = FactSpec.ns("*").aggIdProperty("myProperty", id);
    parsing = AggIdPropertyMatcher.matches(spec);
  }

  @Benchmark
  @Fork(value = 1, warmups = 1)
  public void withoutPrematching(Blackhole blackhole) {
    blackhole.consume(parsing.test(testFact));
  }
}
