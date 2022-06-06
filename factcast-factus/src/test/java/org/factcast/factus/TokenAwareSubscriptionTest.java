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
import org.factcast.factus.FactusImpl.TokenAwareSubscription;
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
  @InjectMocks TokenAwareSubscription uut;

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
