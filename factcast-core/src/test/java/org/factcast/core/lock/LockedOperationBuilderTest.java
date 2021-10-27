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
package org.factcast.core.lock;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.Test;

class LockedOperationBuilderTest {

  final DeprecatedLockedOperationBuilder uut =
      new DeprecatedLockedOperationBuilder(mock(FactStore.class), "ns");

  @Test
  void testAttemptNullContracts() {
    assertThrows(NullPointerException.class, () -> uut.on(UUID.randomUUID()).attempt(null));
  }

  @Test
  void testAttemptAbortsOnNull() {
    assertThrows(
        AttemptAbortedException.class, () -> uut.on(UUID.randomUUID()).attempt(() -> null));
  }

  @Test
  void testAttemptWithoutPublishing() {
    // Note: we will get an exception if we do not return an IntermediatePublishResult.
    // The only way to create such a result is via Attempt.publish, as the only constructor of
    // that class is package-protected and the class is final.
    // So the only thing one can return, if not doing Attempt.publish, Attempt.abort or throw some
    // exception, is null. Not returning anything is prevented by the compiler.
    assertThrows(
        AttemptAbortedException.class, () -> uut.on(UUID.randomUUID()).attempt(() -> null));
  }
}
