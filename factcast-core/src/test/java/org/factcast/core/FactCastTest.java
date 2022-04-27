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
package org.factcast.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.assertj.core.util.Lists;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class FactCastTest {

  @Captor ArgumentCaptor<List<Fact>> facts;

  @Test
  void testFrom() {
    FactStore store = mock(FactStore.class);
    FactCast fc = FactCast.from(store);
    assertTrue(fc instanceof DefaultFactCast);
  }

  @Test
  void testFromNull() {
    Assertions.assertThrows(NullPointerException.class, () -> FactCast.from(null));
  }

  @Test
  void testFromReadOnlyNull() {
    Assertions.assertThrows(NullPointerException.class, () -> FactCast.fromReadOnly(null));
  }

  @Test
  void testFromReadOnly() {
    FactStore store = mock(FactStore.class);
    ReadFactCast fc = FactCast.fromReadOnly(store);
    assertTrue(fc instanceof DefaultFactCast);
  }

  @Test
  public void testPublishFactNPE() {
    FactStore store = mock(FactStore.class);
    FactCast fc = FactCast.from(store);
    assertThrows(NullPointerException.class, () -> fc.publish((Fact) null));
    assertThrows(NullPointerException.class, () -> fc.publish((List<Fact>) null));
  }

  @Test
  void lock1Delegates() {
    FactStore store = mock(FactStore.class);
    FactCast fc = FactCast.from(store);
    FactSpec fs = FactSpec.ns("foo");
    fc.lock(fs);
    verify(fc).lock(eq(Lists.newArrayList(fs)));
  }

  @Test
  void lockArrayDelegates() {
    FactStore store = mock(FactStore.class);
    FactCast fc = FactCast.from(store);
    FactSpec fs1 = FactSpec.ns("foo");
    FactSpec fs2 = FactSpec.ns("bar");
    fc.lock(fs1, fs2);
    verify(fc).lock(eq(Lists.newArrayList(fs1, fs2)));
  }
}
