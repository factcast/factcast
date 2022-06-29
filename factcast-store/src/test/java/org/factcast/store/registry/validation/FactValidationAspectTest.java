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
package org.factcast.store.registry.validation;

import java.util.*;
import java.util.stream.*;

import org.aspectj.lang.ProceedingJoinPoint;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;
import org.factcast.core.TestFact;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

public class FactValidationAspectTest {

  final ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);

  final FactValidator v = mock(FactValidator.class);

  final FactValidationAspect uut = new FactValidationAspect(v);

  final Fact f = Fact.builder().ns("ns").type("type").version(1).buildWithoutPayload();

  @Test
  void testInterceptPublish() throws Throwable {

    when(jp.getArgs()).thenReturn(new Object[] {Collections.singletonList(f)});
    when(v.validate(f)).thenReturn(new LinkedList<>());

    uut.interceptPublish(jp);

    verify(jp).proceed();
  }

  @Test
  void testInterceptPublishInBulk() throws Throwable {

    List<Fact> facts = new ArrayList<>();
    facts.add(f);
    for (int i = 0; i < 10; i++) {
      facts.add(TestFact.copy(f));
    }

    when(jp.getArgs()).thenReturn(new Object[] {facts});
    when(v.validate(f)).thenReturn(new LinkedList<>());

    uut.interceptPublish(jp);

    verify(jp).proceed();
    for (Fact f : facts) {
      verify(v).validate(f);
    }
  }

  @Test
  void testInterceptPublishConditional() throws Throwable {

    when(jp.getArgs()).thenReturn(new Object[] {Collections.singletonList(f)});
    when(v.validate(f)).thenReturn(new LinkedList<>());

    uut.interceptPublishIfUnchanged(jp);

    verify(jp).proceed();
  }

  @Test
  void testInterceptPublishPropagatesErros() throws Throwable {

    when(jp.getArgs()).thenReturn(new Object[] {Collections.singletonList(f)});
    when(v.validate(f)).thenReturn(Collections.singletonList(new FactValidationError("doing")));

    try {
      uut.interceptPublish(jp);
      fail();
    } catch (FactValidationException e) {
      // expected
    }
    verify(jp, never()).proceed();
  }

  @Test
  void testNoParallelization() {
    Stream<Fact> stream = mock(Stream.class);
    uut.parallelizeIfNecessary(Collections.singletonList(f), stream);
    verifyNoInteractions(stream);
  }

  @Test
  void testParallelization() {
    Stream<Fact> stream = mock(Stream.class);
    List<Fact> l = new ArrayList<>(FactValidationAspect.MINIMUM_FACT_LIST_SIZE_TO_GO_PARALLEL + 10);
    for (int i = 0; i < FactValidationAspect.MINIMUM_FACT_LIST_SIZE_TO_GO_PARALLEL + 1; i++) {
      l.add(f);
    }
    uut.parallelizeIfNecessary(l, stream);
    verify(stream).parallel();
    verifyNoMoreInteractions(stream);
  }
}
