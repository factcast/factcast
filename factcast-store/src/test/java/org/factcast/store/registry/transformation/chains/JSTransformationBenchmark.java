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
package org.factcast.store.registry.transformation.chains;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.SneakyThrows;
import org.factcast.core.util.FactCastJson;
import org.factcast.store.internal.script.graaljs.GraalJSEngineFactory;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.Main;
import org.openjdk.jmh.annotations.*;

public class JSTransformationBenchmark {

  static final List<String> steps =
      List.of(
          "function transform(e) {e.new=e.payload.name}",
          "function transform(e) {e.newField=true}",
          "function transform(e) {if(e.payload.meta==null) {e.payload.meta=e.payload.anonMeta;e.payload.meta.organisation=e.payload.lsoOrganisationId;}}");

  static final GraalJSEngineFactory factory = new GraalJSEngineFactory();

  // Treads, amount,    total time,   approach
  // 1,      100_000,   2401ms,       new
  // 1,      100_000,   2375ms,       new
  // 100,    100_000,   2649ms,       new
  // 100,    100_000,   2694ms,       new

  // 1,      100_000,   2443ms,       old
  // 1,      100_000,   2643ms,       old
  // 100,    100_000,   2827ms,       old
  // 100,    100_000,   2487ms,       old

  /// reuse context & engine
  ///  223
  ///
  /// reuse engine, rebuild context
  ///  217
  ///
  /// traditional
  ///  225

  static final int THREADS = 1000;
  static final int AMOUNT = 100_000;

  static final JsTransformer transformer = new JsTransformer(factory);
  static final String script = transformationScript(steps);

  @Test
  @SneakyThrows
  void runWithParallelEngines() {

    final ExecutorService executor = Executors.newFixedThreadPool(THREADS);

    System.out.println("Transforming " + AMOUNT + " facts in " + THREADS + " threads");
    for (int i = 0; i < AMOUNT; i++) {
      executor.submit(
          () -> {
            transformer.runJSTransformation_reuseContext(TestData.exampleFactAsNode(), script);
          });
    }
    executor.shutdown();
    executor.awaitTermination(20, TimeUnit.SECONDS);
  }

  class TestData {
    static String exampleFactAsJson =
        """
  {
  "header" : {
    "id" : "4c9ef99d-0000-0000-0000-b00723606aa2"
    "ns" : "lngmarketing",
    "meta" : {
      "_ts" : 1702369246379,
      "_ser" : 2653562,
      "source" : "lng-marketing",
      "actorUser" : "4c9ef99d-c898-49da-8a84-b00723606aa2",
      "sellingProcedure" : "MANUAL_ALLOCATION",
      "actorOrganisation" : "e3ab997f-71f9-4b21-946a-911f37236b38",
      "lngOfferBusinessId" : "LNO-1"
    },
    "type" : "OfferCreated",
    "aggIds" : [ "e3ab997f-71f9-4b21-946a-911f37236b38", "9f6e8612-e26e-4608-8173-eb8516d47d6b", "3c247137-6d07-41cc-89fc-216c3376ba32" ],
    "version" : 1
  },
  "payload" : {
    "meta" : {
      "amr" : [ "UserAccount:4c9ef99d-c898-49da-8a84-b00723606aa2" ],
      "user" : "4c9ef99d-c898-49da-8a84-b00723606aa2",
      "timestamp" : "2023-12-12T09:20:46.2699538+01:00",
      "organisation" : "e3ab997f-71f9-4b21-946a-911f37236b38"
    },
    "name" : "Super LNG BUY NOW!!!",
    "offerId" : "3c247137-6d07-41cc-89fc-216c3376ba32",
    "fixedSlot" : true,
    "terminalId" : "9f6e8612-e26e-4608-8173-eb8516d47d6b",
    "description" : "You will not regret buying this juicy LNG!",
    "publication" : "2024-01-01T01:00:00+01:00",
    "biddingWindow" : {
      "end" : "2024-02-04T17:00:00+01:00",
      "start" : "2024-02-04T09:00:00+01:00"
    },
    "serviceRuntime" : {
      "end" : "2024-04-02T18:00:00+02:00",
      "start" : "2024-04-02T10:00:00+02:00"
    },
    "availableAmount" : 2,
    "hideReservePrice" : true,
    "sellingProcedure" : "MANUAL_ALLOCATION",
    "lsoOrganisationId" : "e3ab997f-71f9-4b21-946a-911f37236b38",
    "additionalServices" : [ {
      "serviceType" : "REGASIFICATION",
      "servicePrice" : {
        "value" : "0.99",
        "currency" : "EUR"
      },
      "regasificationService" : {
        "quantity" : {
          "amount" : 2000,
          "flowUnitId" : "93b2246a-0027-4842-af6f-f33aa04098b5"
        },
        "displayName" : "Regasification Nr.1"
      },
      "availableAmountPerMainServices" : 1,
      "minimumPurchaseAmountPerMainServices" : 1
    }, {
      "serviceType" : "REGASIFICATION",
      "servicePrice" : {
        "value" : "1",
        "currency" : "EUR"
      },
      "regasificationService" : {
        "quantity" : {
          "amount" : 3000,
          "flowUnitId" : "93b2246a-0027-4842-af6f-f33aa04098b5"
        },
        "displayName" : "Regasification Nr.2"
      },
      "availableAmountPerMainServices" : 3,
      "minimumPurchaseAmountPerMainServices" : 2
    } ],
    "lngOfferBusinessId" : "LNO-1",
    "mainStorageService" : {
      "quantity" : {
        "amount" : 1000,
        "commodityUnitId" : "b7a50cbe-c479-477d-b97b-525f22134e4b"
      },
      "displayName" : "Super storage"
    },
    "minimumPurchaseAmount" : 1,
    "mainRegasificationService" : {
      "quantity" : {
        "amount" : 1000,
        "flowUnitId" : "93b2246a-0027-4842-af6f-f33aa04098b5"
      },
      "displayName" : "Regasification"
    },
    "reservePriceForMainServices" : {
      "value" : "1000",
      "currency" : "EUR"
    }
  }
}
""";
    private static int count = 0;

    @SneakyThrows
    public static JsonNode exampleFactAsNode() {
      JsonNode tree = FactCastJson.readTree(exampleFactAsJson);
      ((ObjectNode) tree.get("header")).put("id", new UUID(0, count++).toString());
      return tree;
    }
  }

  static String transformationScript(List<String> steps) {
    StringBuilder sb = new StringBuilder();
    sb.append("var steps = [");
    sb.append(String.join(",", steps));
    sb.append("]; ");
    sb.append("function transform(event) { steps.forEach( function(f){f(event)} ); }");
    return sb.toString();
  }

  /// //

  @Benchmark
  @Warmup(iterations = 1)
  @Measurement(iterations = 3)
  @BenchmarkMode({Mode.AverageTime})
  @OutputTimeUnit(TimeUnit.MILLISECONDS)
  @Fork(1)
  @Threads(1)
  public void benchmark() {
    runWithParallelEngines();
  }

  public static void main(String[] args) throws Exception {
    Main.main(args);
    new JSTransformationBenchmark().benchmark();
  }
}
