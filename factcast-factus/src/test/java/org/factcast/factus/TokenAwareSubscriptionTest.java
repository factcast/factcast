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
package org.factcast.factus;

import java.io.IOException;
import lombok.SneakyThrows;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.projection.WriterToken;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenAwareSubscriptionTest {

  @Mock private Subscription sub;
  @Mock private WriterToken tkn;
  @InjectMocks private TokenAwareSubscription uut;

  @Nested
  class WhenClosing {
    @BeforeEach
    void setup() {}

    @SneakyThrows
    @Test
    void testCloseAlsoReleasesToken() {
      uut.close();
      Mockito.verify(sub).close();
      Mockito.verify(tkn).close();
    }

    @SneakyThrows
    @Test
    void testCloseAlsoReleasesTokenEvenIfExceptionIsThrown() {
      Mockito.doThrow(IOException.class).when(sub).close();

      Assertions.assertThrows(Exception.class, () -> uut.close());
      Mockito.verify(sub).close();
      Mockito.verify(tkn).close();
    }
  }

  @Nested
  class WhenAwaitingCatchup {

    @Test
    void delegates() {
      uut.awaitCatchup();
      Mockito.verify(sub).awaitCatchup();
    }
  }

  @Nested
  class WhenAwaitingCatchupWithMax {
    private final long WAIT_TIME_IN_MILLIS = 51;

    @SneakyThrows
    @Test
    void delegates() {
      uut.awaitCatchup(WAIT_TIME_IN_MILLIS);
      Mockito.verify(sub).awaitCatchup(WAIT_TIME_IN_MILLIS);
    }
  }

  @Nested
  class WhenAwaitingComplete {

    @Test
    void delegates() {
      uut.awaitComplete();
      Mockito.verify(sub).awaitComplete();
    }
  }

  @Nested
  class WhenAwaitingCompleteWithMax {
    private final long WAIT_TIME_IN_MILLIS = 96;

    @SneakyThrows
    @Test
    void delegates() {
      uut.awaitComplete(WAIT_TIME_IN_MILLIS);
      Mockito.verify(sub).awaitComplete(WAIT_TIME_IN_MILLIS);
    }
  }
}
