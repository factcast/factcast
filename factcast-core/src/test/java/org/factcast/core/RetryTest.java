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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.factcast.core.store.FactStore;
import org.factcast.core.store.RetryableException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class RetryTest {

  @Mock FactStore fs;

  @Test
  void testHappyPath() {
    // arrange

    // Note: we intended the retryableException to be passed from store to
    // factcast,
    // so we mock the store here

    doThrow(new RetryableException(new IllegalStateException())) //
        .doThrow(new RetryableException(new IllegalArgumentException())) //
        .doNothing() //
        .when(fs)
        .publish(anyList());

    // retry(5) wraps the factcast instance
    FactCast uut = FactCast.from(fs).retry(5);

    // act
    uut.publish(Fact.builder().ns("ns").type("type").buildWithoutPayload());
    verify(fs, times(3)).publish(anyList());
    verifyNoMoreInteractions(fs);
  }

  @Test
  void testWrapsOnlyOnce() {
    FactCast uut = FactCast.from(fs).retry(3);
    FactCast doubleWrapped = uut.retry(5);

    assertSame(doubleWrapped, uut);
  }

  @Test
  void testMaxRetries() {
    int maxRetries = 3;
    // as we literally "re"-try, we expect the original attempt plus
    // maxRetries:
    int expectedPublishAttempts = maxRetries + 1;
    doThrow(new RetryableException(new RuntimeException(""))).when(fs).publish(anyList());
    FactCast uut = FactCast.from(fs).retry(maxRetries);

    assertThrows(
        MaxRetryAttemptsExceededException.class,
        () -> uut.publish(Fact.builder().ns("foo").type("type").build("{}")));

    verify(fs, times(expectedPublishAttempts)).publish(anyList());
    verifyNoMoreInteractions(fs);
  }

  @Test
  public void testWrapIllegalArguments() {
    assertThrows(IllegalArgumentException.class, () -> FactCast.from(fs).retry(3, -1));

    assertThrows(IllegalArgumentException.class, () -> FactCast.from(fs).retry(0, 10));

    assertThrows(IllegalArgumentException.class, () -> FactCast.from(fs).retry(-2, 10));
    assertNotNull(FactCast.from(fs).retry(1, 0));
  }

  @Test
  void testThrowNonRetryableException() {
    int maxRetries = 3;
    doThrow(new UnsupportedOperationException("not retryable")).when(fs).publish(anyList());
    FactCast uut = FactCast.from(fs).retry(maxRetries);

    assertThrows(
        UnsupportedOperationException.class,
        () -> uut.publish(Fact.builder().ns("foo").type("type").build("{}")));

    verify(fs, times(1)).publish(anyList());
    verifyNoMoreInteractions(fs);
  }
}
