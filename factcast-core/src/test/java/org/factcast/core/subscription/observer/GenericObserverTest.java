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
package org.factcast.core.subscription.observer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import lombok.NonNull;
import org.factcast.core.TestFact;
import org.factcast.core.TestHelper;
import org.junit.jupiter.api.*;

public class GenericObserverTest {

  @Test
  void testMap() {
    GenericObserver<Integer> i =
        spy(
            new GenericObserver<Integer>() {
              @Override
              public void onNext(@NonNull Integer element) {}
            });
    FactObserver mapped = i.map(f -> 4);
    verify(i, never()).onCatchup();
    mapped.onCatchup();
    verify(i).onCatchup();
    verify(i, never()).onError(any());
    mapped.onError(
        new Throwable("ignore me") {

          private static final long serialVersionUID = 1L;

          @Override
          public StackTraceElement[] getStackTrace() {
            return new StackTraceElement[0];
          }
        });
    verify(i).onError(any());
    verify(i, never()).onComplete();
    mapped.onComplete();
    verify(i).onComplete();
    verify(i, never()).onNext(any());
    mapped.onNext(new TestFact());
    verify(i).onNext(4);
  }

  @Test
  void testMapNull() {
    Assertions.assertThrows(
        NullPointerException.class,
        () -> {
          GenericObserver<Integer> i = element -> {};
          i.map(null);
        });
  }

  @Test
  public void testOnErrorNullParameter() {
    GenericObserver<Integer> uut = e -> {};
    TestHelper.expectNPE(
        () -> {
          uut.onError(null);
        });
  }
}
