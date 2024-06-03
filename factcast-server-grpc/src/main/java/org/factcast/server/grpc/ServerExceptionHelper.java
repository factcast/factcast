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
package org.factcast.server.grpc;

import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.experimental.UtilityClass;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.AuthenticationException;

@UtilityClass
public class ServerExceptionHelper {

  public static StatusRuntimeException translate(Throwable e, Metadata meta) {
    if (e instanceof StatusRuntimeException sre) // prevent double wrap
    {
      return sre;
    } else if (e instanceof RuntimeException
        && e.getClass().getName().startsWith("org.factcast.core")) {
      return new StatusRuntimeException(Status.UNKNOWN, addMetaData(meta, e));
    } else if (e instanceof UnsupportedOperationException) {
      // UNIMPLEMENTED is technically not fully correct but best we can do here
      return new StatusRuntimeException(Status.UNIMPLEMENTED, meta);
    } else if (e instanceof AuthenticationException) {
      if (e instanceof AuthenticationCredentialsNotFoundException) {
        return new StatusRuntimeException(Status.UNAUTHENTICATED, meta);
      } else return new StatusRuntimeException(Status.PERMISSION_DENIED, meta);
    } else {
      return new StatusRuntimeException(Status.UNKNOWN, meta);
    }
  }

  public static StatusRuntimeException translate(Throwable e) {
    return translate(e, new Metadata());
  }

  private static Metadata addMetaData(Metadata metadata, Throwable e) {
    metadata.put(
        Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
    metadata.put(
        Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
        e.getClass().getName().getBytes());
    return metadata;
  }
}
