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

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.*;
import org.mockito.Mockito;

class AttemptAbortedExceptionTest {
  @Test
  void passesParameters() throws Exception {
    assertThat(new AttemptAbortedException("foo").getMessage()).isEqualTo("foo");
    Exception e = Mockito.mock(Exception.class);
    assertThat(new AttemptAbortedException(e).getCause()).isSameAs(e);
  }
}
