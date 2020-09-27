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
package org.factcast.store.pgsql.registry.validation;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
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

  private final FactValidator validator;

  @SuppressWarnings("unchecked")
  @Around("execution(public void org.factcast.core.store.FactStore.publish(*))")
  public Object interceptPublish(ProceedingJoinPoint joinPoint) throws Throwable {
    log.trace("intercepting publish()");

    Object[] args = joinPoint.getArgs();
    List<? extends Fact> facts = (List<? extends Fact>) args[0];
    validate(facts);

    return joinPoint.proceed();
  }

  private void validate(List<? extends Fact> facts) {

    List<FactValidationError> errors = new LinkedList<>();

    facts.forEach(f -> errors.addAll(validator.validate(f)));

    if (!errors.isEmpty())
      throw new FactValidationException(
          errors.stream().map(FactValidationError::toString).collect(Collectors.toList()));
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
