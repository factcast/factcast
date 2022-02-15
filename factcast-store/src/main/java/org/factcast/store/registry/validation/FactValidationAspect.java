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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.factcast.core.Fact;
import org.factcast.core.FactValidationException;

@Aspect
@Slf4j
@RequiredArgsConstructor
public class FactValidationAspect {

  public static final int MINIMUM_FACT_LIST_SIZE_TO_GO_PARALLEL = 10;
  private final FactValidator validator;

  // not hogging the common FJP. Also limiting parallelism to less of what the common pool has.
  private static final ForkJoinPool validationPool =
      new ForkJoinPool((int) Math.abs(Runtime.getRuntime().availableProcessors() / 1.5));

  @SuppressWarnings("unchecked")
  @Around("execution(public void org.factcast.core.store.FactStore.publish(*))")
  public Object interceptPublish(ProceedingJoinPoint joinPoint) throws Throwable {
    log.trace("intercepting publish()");

    Object[] args = joinPoint.getArgs();
    List<? extends Fact> facts = (List<? extends Fact>) args[0];
    validate(facts);

    return joinPoint.proceed();
  }

  private void validate(List<? extends Fact> facts)
      throws ExecutionException, InterruptedException {
    List<FactValidationError> errors;
    if (facts.size() >= MINIMUM_FACT_LIST_SIZE_TO_GO_PARALLEL) {
      errors = validationPool.submit(() -> validate(facts.parallelStream())).get();
    } else {
      errors = validate(facts.stream());
    }

    if (!errors.isEmpty())
      throw new FactValidationException(
          errors.stream().map(FactValidationError::toString).collect(Collectors.toList()));
  }

  private List<FactValidationError> validate(Stream<? extends Fact> stream) {
    return stream.map(validator::validate).flatMap(Collection::stream).collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  @Around("execution(public boolean org.factcast.core.store.FactStore.publishIfUnchanged(..))")
  public Object interceptPublishIfUnchanged(ProceedingJoinPoint joinPoint) throws Throwable {
    log.trace("intercepting publishIfUnchanged()");
    Object[] args = joinPoint.getArgs();
    List<? extends Fact> facts = (List<? extends Fact>) args[0];
    validate(facts);

    return joinPoint.proceed();
  }
}
