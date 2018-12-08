/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.factcast.core.store.RetryableException;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class Retry {
    private static final ClassLoader classLoader = Retry.class.getClassLoader();

    static final long DEFAULT_WAIT_TIME_MILLIS = 10;

    @SuppressWarnings("unchecked")
    public static <T extends ReadFactCast> T wrap(boolean readOnly, T toWrap, int maxRetries,
            long minimumWaitTimeMillis) {

        if (minimumWaitTimeMillis < 0) {
            throw new IllegalArgumentException("minimumWaitTimeMillis must be >= 0");
        }

        Class<?> interfaceToProxy = readOnly ? ReadFactCast.class : FactCast.class;
        return (T) Proxy.newProxyInstance(classLoader, new Class[] { interfaceToProxy },
                new RetryProxyInvocationHandler(toWrap, maxRetries, minimumWaitTimeMillis));
    }

    @RequiredArgsConstructor
    private static class RetryProxyInvocationHandler implements InvocationHandler {
        @NonNull
        final Object delegateObject;

        final int maxRetries;

        final long minimumWaitTimeMillis;

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
                        if (retryAttempt++ < maxRetries) {
                            sleep(minimumWaitTimeMillis);
                            log.warn("Retrying attempt {}/{}", retryAttempt, maxRetries);
                        }
                    } else {
                        throw cause;
                    }
                }
            } while (retryAttempt <= maxRetries);
            throw new MaxRetryAttemptsExceededException(
                    "Exceeded max retry attempts of '" + description + "', giving up.");
        }

        private String toString(Method method) {
            String args = Arrays.stream(method.getParameterTypes())
                    .map(t -> t.getSimpleName())
                    .collect(Collectors.joining(", "));
            return method.getDeclaringClass().getSimpleName() + "::" + method.getName() + "(" + args
                    + ")";
        }

        private void sleep(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignore) {
                //
            }
        }
    }
}
