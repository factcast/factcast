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
package org.factcast.store.internal;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.factcast.core.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UnconditionalPublishQueueTest {

  @Mock private PgFactStore pgFactStore;

  @Mock private Fact fact;

  private UnconditionalPublishQueue underTest;

  @BeforeEach
  void setUp() {
    underTest = new UnconditionalPublishQueue(pgFactStore);
  }

  @Test
  void addAndFlush_should_complete_when_batchPublish_succeeds() throws Exception {
    // Arrange
    List<Fact> facts = Collections.singletonList(fact);

    // Act
    Future<Void> future = underTest.addAndFlush(facts);

    // Assert
    future.get(); // Should not throw
    verify(pgFactStore, timeout(1000)).batchPublish(facts);
  }

  @Test
  void addAndFlush_should_retry_individually_when_batchPublish_fails() throws Exception {
    // Arrange
    Fact fact1 = mock(Fact.class);
    Fact fact2 = mock(Fact.class);
    List<Fact> facts = List.of(fact1, fact2);

    // Simulate batch failure for the full list
    AtomicBoolean failed = new AtomicBoolean(false);
    doAnswer(
            invocation -> {
              if (!failed.getAndSet(true)) {
                throw new RuntimeException("Batch failure");
              }
              return null;
            })
        .when(pgFactStore)
        .batchPublish(anyList());

    // Act
    Future<Void> future = underTest.addAndFlush(facts);

    // Assert
    // Wait for completion (it should complete now that retries succeed)
    future.get();

    verify(pgFactStore, times(2)).batchPublish(facts); // Initial failure + retry
  }

  @Test
  void addAndFlush_should_handle_concurrent_lists_with_duplicates() throws Exception {

    CountDownLatch cdl = new CountDownLatch(1);

    UnconditionalPublishQueue uut = spy(underTest);

    doAnswer(
            i -> {
              cdl.await();
              return i.callRealMethod();
            })
        .when(uut)
        .flush(anyLong());

    // Arrange
    Fact fact1 = mock(Fact.class);
    Fact fact2 = mock(Fact.class);
    Fact fact3 = mock(Fact.class);

    List<Fact> facts1 = List.of(fact1);
    List<Fact> facts2 = List.of(fact2);
    List<Fact> facts3 = List.of(fact3);

    List<Fact> allFacts = List.of(fact1, fact2, fact3);

    // Simulate batch failure for combined list and dup failure for individual lists
    doAnswer(
            invocation -> {
              List<Fact> facts = invocation.getArgument(0);
              if (facts.size() == 3) {
                throw new DuplicateFactException("Batch failure");
              }
              if (facts.equals(facts1) || facts.equals(facts3)) {
                throw new DuplicateFactException("Duplicate fact");
              }
              return null;
            })
        .when(pgFactStore)
        .batchPublish(anyList());

    // Act
    Future<Future<Void>> future1 = CompletableFuture.supplyAsync(() -> uut.addAndFlush(facts1));
    Future<Future<Void>> future2 = CompletableFuture.supplyAsync(() -> uut.addAndFlush(facts2));
    Future<Future<Void>> future3 = CompletableFuture.supplyAsync(() -> uut.addAndFlush(facts3));

    cdl.countDown();

    // Assert
    assertThrows(ExecutionException.class, () -> future1.get().get());
    future2.get().get(); // Should succeed
    assertThrows(ExecutionException.class, () -> future3.get().get());

    // this one failed:
    verify(pgFactStore, times(1)).batchPublish(argThat(l -> l.containsAll(allFacts)));
    // so those need to happen
    verify(pgFactStore, times(1)).batchPublish(eq(facts1));
    verify(pgFactStore, times(1)).batchPublish(eq(facts2));
    verify(pgFactStore, times(1)).batchPublish(eq(facts3));
  }
}
