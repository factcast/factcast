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
package org.factcast.factus.lock;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InLockedOperationTest {

  @InjectMocks private InLockedOperation underTest;

  @Test
  void initialIsFalse() {
    InLockedOperation.assertNotInLockedOperation();
  }

  @Test
  void failsIfLocked() {
    try {
      InLockedOperation.enterLockedOperation();
      assertThrows(
          IllegalStateException.class, () -> InLockedOperation.assertNotInLockedOperation());
    } finally {
      InLockedOperation.exitLockedOperation();
    }
  }

  @Test
  void doesNotFailIfUnLocked() {
    InLockedOperation.enterLockedOperation();
    InLockedOperation.exitLockedOperation();

    InLockedOperation.assertNotInLockedOperation();
  }
}
