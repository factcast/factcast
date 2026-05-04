/*
 * Copyright © 2017-2024 factcast.org
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

import io.grpc.Deadline;
import jakarta.annotation.Nullable;
import org.factcast.grpc.api.gen.RemoteFactStoreGrpc;

public interface GrpcStubs {
  default RemoteFactStoreGrpc.RemoteFactStoreBlockingStub uncompressedBlocking() {
    return uncompressedBlocking(null);
  }

  RemoteFactStoreGrpc.RemoteFactStoreBlockingStub uncompressedBlocking(@Nullable Deadline deadline);

  /**
   * @return RemoteFactStoreBlockingStub with compression if already configured
   */
  default RemoteFactStoreGrpc.RemoteFactStoreBlockingStub blocking() {
    return blocking(null);
  }

  /**
   * @param deadline null or deadline after which request will be terminated
   * @return RemoteFactStoreBlockingStub with compression if already configured
   */
  RemoteFactStoreGrpc.RemoteFactStoreBlockingStub blocking(@Nullable Deadline deadline);

  /**
   * @return RemoteFactStoreStub with compression if already configured
   */
  // deadline does not make sense here?
  RemoteFactStoreGrpc.RemoteFactStoreStub nonBlocking();

  GrpcStubs compression(@Nullable String compressionId);
}
