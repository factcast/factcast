/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.registry.transformation.cache;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.transformation.chains.TransformationChain;
import org.joda.time.DateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Spy;

import lombok.SneakyThrows;

public abstract class AbstractTransformationCacheTest {
  protected TransformationCache uut;

  @Spy protected RegistryMetrics registryMetrics = new NOPRegistryMetrics();

  @BeforeEach
  public void init() {
    uut = createUUT();
  }

  protected abstract TransformationCache createUUT();

  @Test
  void testEmptyFind() {
    Optional<Fact> fact = uut.find(UUID.randomUUID(), 1, "1");

    assertThat(fact.isPresent()).isFalse();

    verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_MISS);
  }

  @Test
  void testFindAfterPut() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(fact, chainId);

    Optional<Fact> found = uut.find(fact.id(), fact.version(), chainId);

    assertThat(found.isPresent()).isTrue();
    assertEquals(fact, found.get());
    verify(registryMetrics).count(RegistryMetrics.EVENT.TRANSFORMATION_CACHE_HIT);
  }

  @Test
  void testCompact() {
    Fact fact = Fact.builder().ns("ns").type("type").id(UUID.randomUUID()).version(1).build("{}");
    String chainId = "1-2-3";

    uut.put(fact, chainId);

    // clocks aren't synchronized so Im gonna add an hour here :)
    uut.compact(DateTime.now().plusHours(1));

    Optional<Fact> found = uut.find(fact.id(), fact.version(), chainId);

    assertThat(found.isPresent()).isFalse();
  }

  @Test
  void testNullContracts() {
    assertNpe(() -> uut.find(null, 1, "1"));
    assertNpe(() -> uut.find(UUID.randomUUID(), 1, null));
    assertNpe(() -> uut.put(null, ""));
    assertNpe(() -> uut.put(Fact.builder().buildWithoutPayload(), null));
  }

  private void assertNpe(Executable r) {
    assertThrows(NullPointerException.class, r);
  }

  @Test
  void testRespectsChainId() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(f, "foo");
    assertThat(uut.find(f.id(), 1, "xoo")).isEmpty();
  }

  @Test
  void testDoesNotFindUnknown() {
    uut.find(UUID.randomUUID(), 1, "foo");
  }

  @Test
  void testHappyPath() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(f, "foo");
    assertThat(uut.find(f.id(), 1, "foo")).contains(f);
  }

  @Test
  void testRespectsVersion() {
    Fact f = Fact.builder().ns("name").type("type").version(1).build("{}");

    uut.put(f, "foo");
    assertThat(uut.find(f.id(), 2, "foo")).isEmpty();
  }

  @Test
  void testEmptyBatchPut_noException() {
    uut.put(emptyList());
  }

  @Test
  void testEmptyBatchFind_noException() {
    uut.find(emptyList());
  }

  /**
   * Here we are putting two facts f1 and fForRemoval manually (with single put calls) into the
   * database, and then three more (f2, f3, f4) with a batch put.
   *
   * <p>Then we batch-ask the cache for f1, f2, f3, f4 and a new fact f5 which is not in the cache.
   *
   * <p>We also ask with single find calls to see everything works together.
   *
   * <p>Then we compact the cache and expect fForRemoval to be removed, as its timestamp was not
   * updated by the find. f1, however, must have been updated and must be returned by the find, as
   * well as f2, f3 and f4 which were inserted after the timestamp we use for compaction.
   */
  @Test
  @SneakyThrows
  void testBatchFindAfterBatchPut() {
    // INIT
    TransformationKey key = TransformationKey.of("ns", "type");

    String chainId = "1-2-3";

    TransformationChain chain = mock(TransformationChain.class);
    when(chain.id()).thenReturn(chainId);

    // put one fact that we will read later manually into the cache
    var factId1 = UUID.randomUUID();
    Fact factAlreadyInStore =
        Fact.builder().ns("ns").type("type").id(factId1).version(2).build("{\"y\":0}");
    uut.put(factAlreadyInStore, chainId);

    // put another fact that we will not read later manually into the cache
    var factForRemovalId = UUID.randomUUID();
    Fact factForRemoval =
        Fact.builder().ns("ns").type("type").id(factForRemovalId).version(2).build("{\"y\":1}");
    uut.put(factForRemoval, chainId);

    // check for both the transformed ones are returned by the cache
    var factBeforeTransformation1 =
        Fact.builder().ns("ns").type("type").id(factId1).version(1).build("{\"x\":0}");
    var oldFactWithTargetVersion1 =
        new FactWithTargetVersion(0, factBeforeTransformation1, 2, key, chain);

    var oldFactForRemoval =
        Fact.builder().ns("ns").type("type").id(factForRemovalId).version(1).build("{\"x\":1}");
    var oldFactForRemovalWithTargetVersion =
        new FactWithTargetVersion(1, oldFactForRemoval, 2, key, chain);

    var preCheck =
        uut.find(newArrayList(oldFactWithTargetVersion1, oldFactForRemovalWithTargetVersion));

    assertThat(preCheck)
        .containsOnly(
            // that one must be there as well
            entry(oldFactWithTargetVersion1, factAlreadyInStore),
            entry(oldFactForRemovalWithTargetVersion, factForRemoval));

    // if we later compact with this time, the factAlreadyInStore should be removed - unless the
    // timestamp was updated when reading from the cache. so we store the current time to try that
    // out later.
    // we need to wait here for a second, as db timestamps are not super precise
    Thread.sleep(1_000);
    var before = DateTime.now();

    // batch-inserts some facts
    var factId2 = UUID.randomUUID();
    var factId3 = UUID.randomUUID();
    var factId4 = UUID.randomUUID();

    var fact2 = Fact.builder().ns("ns").type("type").id(factId2).version(2).build("{\"y\":2}");
    var fact3 = Fact.builder().ns("ns").type("type").id(factId3).version(2).build("{\"y\":3}");
    var fact4 = Fact.builder().ns("ns").type("type").id(factId4).version(2).build("{\"y\":4}");

    uut.put(
        newArrayList(
            new FactWithTargetVersion(2, fact2, 2, key, chain),
            new FactWithTargetVersion(3, fact3, 2, key, chain),
            new FactWithTargetVersion(4, fact4, 2, key, chain)));

    // this one is not in the cache
    var factId5 = UUID.randomUUID();

    // RUN

    // these facts should get transformed to version 2

    var factBeforeTransformation2 =
        Fact.builder().ns("ns").type("type").id(factId2).version(1).build("{\"x\":2}");
    var factBeforeTransformation3 =
        Fact.builder().ns("ns").type("type").id(factId3).version(1).build("{\"x\":3}");
    var factBeforeTransformation4 =
        Fact.builder().ns("ns").type("type").id(factId4).version(1).build("{\"x\":4}");
    var factBeforeTransformation5 =
        Fact.builder().ns("ns").type("type").id(factId5).version(1).build("{\"x\":5}");

    var oldFactWithTargetVersion2 =
        new FactWithTargetVersion(1, factBeforeTransformation2, 2, key, chain);
    var oldFactWithTargetVersion3 =
        new FactWithTargetVersion(1, factBeforeTransformation3, 2, key, chain);
    var oldFactWithTargetVersion4 =
        new FactWithTargetVersion(1, factBeforeTransformation4, 2, key, chain);
    var oldFactWithTargetVersion5 =
        new FactWithTargetVersion(1, factBeforeTransformation5, 2, key, chain);

    var results =
        uut.find(
            newArrayList(
                // that was put manually into the cache
                oldFactWithTargetVersion1,
                // do not check for factForRemoval, as that would update the timestamp.
                // this was put with a batch put:
                oldFactWithTargetVersion2,
                oldFactWithTargetVersion3,
                oldFactWithTargetVersion4,
                // this is not in the cache
                oldFactWithTargetVersion5));

    // ASSERT
    assertThat(results)
        .containsOnly(
            // that one must be there as well
            entry(oldFactWithTargetVersion1, factAlreadyInStore),
            // the facts from the batch put
            entry(oldFactWithTargetVersion2, fact2),
            entry(oldFactWithTargetVersion3, fact3),
            entry(oldFactWithTargetVersion4, fact4)
            // the one that was not in the cache should be missing
            );

    // make sure single finds also returns the batch-inserted ones
    assertThat(uut.find(factId1, 2, chainId)).isPresent().get().isEqualTo(factAlreadyInStore);
    // do not check for factForRemoval, as that would update the timestamp
    assertThat(uut.find(factId2, 2, chainId)).isPresent().get().isEqualTo(fact2);
    assertThat(uut.find(factId3, 2, chainId)).isPresent().get().isEqualTo(fact3);
    assertThat(uut.find(factId4, 2, chainId)).isPresent().get().isEqualTo(fact4);
    // and this one must still be missing of course
    assertThat(uut.find(factId5, 2, chainId)).isNotPresent();

    // check that batchFind updated the timestamps
    // Note: if this fails, that is because the update is triggered asynchronously. Maybe wait a
    // moment here if that happens, or find a smart way to synchronize.
    uut.compact(before);

    // now from the two facts we inserted before the find, only the one
    // loaded by find should still be there, the other one must have been removed,
    // which proves the find method correctly updates the timestamps

    var resultsAfterCompact =
        uut.find(
            newArrayList(
                // both were put manually into the cache
                oldFactWithTargetVersion1,
                oldFactForRemovalWithTargetVersion,
                // this was put with a batch put
                oldFactWithTargetVersion2,
                oldFactWithTargetVersion3,
                oldFactWithTargetVersion4,
                // this is not in the cache
                oldFactWithTargetVersion5));

    assertThat(resultsAfterCompact)
        .containsOnly(
            // that one must still be there!
            entry(oldFactWithTargetVersion1, factAlreadyInStore),
            // the one we didn't read before the compact (factForRemoval) must be missing
            // the facts from the batch put
            entry(oldFactWithTargetVersion2, fact2),
            entry(oldFactWithTargetVersion3, fact3),
            entry(oldFactWithTargetVersion4, fact4)
            // the one that was not in the cache should be missing
            );
  }
}
