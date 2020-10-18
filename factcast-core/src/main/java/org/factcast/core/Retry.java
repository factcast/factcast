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

import com.google.common.base.Preconditions;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Collectors;
import lombok.Generated;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.store.RetryableException;

@Slf4j
@UtilityClass
class Retry {
  @SuppressWarnings("RedundantModifiersUtilityClassLombok")
  private static final ClassLoader classLoader = Retry.class.getClassLoader();

  final long DEFAULT_WAIT_TIME_MILLIS = 10;

  public FactCast wrap(FactCast toWrap, int maxRetryAttempts, long minimumWaitIntervalMillis) {
    Preconditions.checkArgument(maxRetryAttempts > 0, "maxRetryAttempts must be > 0");
    Preconditions.checkArgument(
        minimumWaitIntervalMillis >= 0, "minimumWaitIntervalMillis must be >= 0");

    return (FactCast)
        Proxy.newProxyInstance(
            classLoader,
            new Class[] {FactCast.class},
            new RetryProxyInvocationHandler(toWrap, maxRetryAttempts, minimumWaitIntervalMillis));
  }

  @SuppressWarnings("RedundantModifiersUtilityClassLombok")
  @RequiredArgsConstructor
  private static class RetryProxyInvocationHandler implements InvocationHandler {
    @NonNull final Object delegateObject;

    final int maxRetryAttempts;

    final long minimumWaitIntervalMillis;

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

      if (method.getName().equals("retry")) {
        log.warn("Trying to double-wrap a factcast instance. Ignoring.");
        return proxy;
      }

      String description = toString(method);

      int retryAttempt = 0;
      do {
        try {
          return method.invoke(delegateObject, args);
        } catch (InvocationTargetException ex) {
          Throwable cause = ex.getCause();
          if (cause instanceof RetryableException) {
            RetryableException e = (RetryableException) cause;
            log.warn("{} failed: ", description, e.getCause());
            if (retryAttempt++ < maxRetryAttempts) {
              sleep(minimumWaitIntervalMillis);
              log.warn("Retrying attempt {}/{}", retryAttempt, maxRetryAttempts);
            }
          } else {
            throw cause;
          }
        }
      } while (retryAttempt <= maxRetryAttempts);
      throw new MaxRetryAttemptsExceededException(
          "Exceeded max retry attempts of '" + description + "', giving up.");
    }

    private String toString(Method method) {
      String args =
          Arrays.stream(method.getParameterTypes())
              .map(Class::getSimpleName)
              .collect(Collectors.joining(", "));
      return method.getDeclaringClass().getSimpleName()
          + "::"
          + method.getName()
          + "("
          + args
          + ")";
    }

    @Generated
    private void sleep(long millis) {
      try {
        Thread.sleep(millis);
      } catch (InterruptedException ignore) {
        //
      }
    }
  }
}
