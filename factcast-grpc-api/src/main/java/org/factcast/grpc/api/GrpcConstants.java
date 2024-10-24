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
package org.factcast.grpc.api;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.*;

@Slf4j
@UtilityClass
public class GrpcConstants {
  public static final int DEFAULT_CLIENT_INBOUND_MESSAGE_SIZE = 8 * 1024 * 1024;

  // supposed to prevent a client from shooting himself in the foot. We have seen Events in the wild
  // that are >600kb already.
  public static final int MIN_CLIENT_INBOUND_MESSAGE_SIZE = 2 * 1024 * 1024;

  // supposed to prevent a client from driving the server to OOM
  public static final int MAX_CLIENT_INBOUND_MESSAGE_SIZE = 32 * 1024 * 1024;

  public static int calculateMaxInboundMessageSize(int requested) {
    if (requested > GrpcConstants.MAX_CLIENT_INBOUND_MESSAGE_SIZE) {
      log.warn(
          "A maxInboundMessageSize of {} exceeds the upper limit of {}. Limiting it to upper bound.",
          requested,
          GrpcConstants.MAX_CLIENT_INBOUND_MESSAGE_SIZE);
    }

    if (requested < GrpcConstants.MIN_CLIENT_INBOUND_MESSAGE_SIZE) {
      log.warn(
          "A maxInboundMessageSize of {} is smaller than the lower limit of {}. Limiting it to lower bound.",
          requested,
          GrpcConstants.MIN_CLIENT_INBOUND_MESSAGE_SIZE);
    }

    return Math.max(
        GrpcConstants.MIN_CLIENT_INBOUND_MESSAGE_SIZE,
        Math.min(requested, GrpcConstants.MAX_CLIENT_INBOUND_MESSAGE_SIZE));
  }
}
