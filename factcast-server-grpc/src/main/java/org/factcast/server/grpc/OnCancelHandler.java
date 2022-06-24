/*
 * Copyright © 2017-2022 factcast.org
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

import java.util.concurrent.atomic.*;

import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequestTO;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class OnCancelHandler implements Runnable {
  @NonNull private final GrpcRequestMetadata meta;
  @NonNull private final SubscriptionRequestTO req;
  @NonNull private final AtomicReference<Subscription> subRef;
  @NonNull private final GrpcObserverAdapter observer;

  @Override
  public void run() {

    String clientIdPrefix = meta.clientId().map(c -> c + "|").orElse("unknown|");

    log.debug(
        "{}got onCancel from stream, closing subscription {}", clientIdPrefix, req.debugInfo());
    try {
      subRef.get().close();
    } catch (Exception e) {
      log.debug("{}While closing connection after cancel", clientIdPrefix, e);
    }
    try {
      observer.shutdown();
    } catch (Exception e) {
      log.debug("{}While shutting down observer after cancel", clientIdPrefix, e);
    }
  }
}
