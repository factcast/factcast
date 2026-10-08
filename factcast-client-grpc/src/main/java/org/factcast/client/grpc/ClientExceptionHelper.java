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
package org.factcast.client.grpc;

import com.google.common.collect.Sets;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.Status.Code;
import io.grpc.StatusRuntimeException;
import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.NonNull;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.store.RetryableException;
import org.factcast.core.util.ExceptionHelper;

@Slf4j
@UtilityClass
public class ClientExceptionHelper {

  public static RuntimeException from(Throwable e) {

    Throwable toReturn = e;
    if (e instanceof StatusRuntimeException sre) {

      Metadata md = sre.getTrailers();
      if (md != null) {

        byte[] msgBytes = md.get(Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER));
        byte[] excBytes = md.get(Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER));

        if (excBytes != null) {
          String className = new String(excBytes);
          try {
            Class<?> exc = Class.forName(className);
            Constructor<?> constructor = exc.getConstructor(String.class);
            String msg = new String(Objects.requireNonNull(msgBytes));
            toReturn = (RuntimeException) constructor.newInstance(msg);
          } catch (Exception ex) {
            log.warn("Something went wrong materializing an exception of type {}", className, ex);
          }
        }
      }

      if (isRetryable(toReturn)) {
        return new RetryableException(toReturn);
      }
    }
    return ExceptionHelper.toRuntime(toReturn);
  }

  private static final Set<Code> RETRYABLE_STATUS =
      Sets.newHashSet(
          Status.UNKNOWN.getCode(),
          Status.UNAVAILABLE.getCode(),
          Status.ABORTED.getCode(),
          Status.DEADLINE_EXCEEDED.getCode());

  public static boolean isRetryable(@NonNull Throwable exception) {
    if (exception instanceof RetryableException) {
      return true;
    }

    if (exception instanceof StatusRuntimeException runtimeException) {
      Code s = runtimeException.getStatus().getCode();
      return RETRYABLE_STATUS.contains(s) || isCausedByNetwork(runtimeException);
    }
    return false;
  }

  /**
   * Check for message that indicates an issue caused by a proxy (e.g. LoadBalancer) and therefore
   * warrants a retry.
   */
  private boolean isCausedByNetwork(StatusRuntimeException e) {
    return Optional.ofNullable(e.getMessage())
        .map(m -> m.contains("CANCELLED: RST_STREAM closed stream. HTTP/2 error code: CANCEL"))
        .orElse(false);
  }
}
