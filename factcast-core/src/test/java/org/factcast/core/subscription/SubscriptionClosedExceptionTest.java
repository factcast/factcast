/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.core.subscription;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionClosedExceptionTest {

  @Test
  void passesException() {
    IOException e = new IOException();
    var uut = new SubscriptionClosedException(e);
    assertThat(uut.getCause()).isSameAs(e);
  }

  @Test
  void passesMessage() {
    var msg = "foo";
    var uut = new SubscriptionClosedException(msg);
    assertThat(uut.getMessage()).isSameAs(msg);
  }
}
