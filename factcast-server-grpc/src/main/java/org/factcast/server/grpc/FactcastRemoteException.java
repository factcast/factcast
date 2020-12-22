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

public class FactcastRemoteException {

  public static Throwable of(Throwable e) {

    if (e instanceof RuntimeException && e.getClass().getName().startsWith("org.factcast."))
      return new StatusRuntimeException(Status.UNKNOWN, createMetaData(e));
    else return new StatusRuntimeException(Status.UNKNOWN);
  }

  private static Metadata createMetaData(Throwable e) {
    Metadata metadata = new Metadata();
    metadata.put(
        Metadata.Key.of("msg-bin", Metadata.BINARY_BYTE_MARSHALLER), e.getMessage().getBytes());
    metadata.put(
        Metadata.Key.of("exc-bin", Metadata.BINARY_BYTE_MARSHALLER),
        e.getClass().getName().getBytes());
    return metadata;
  }
}
