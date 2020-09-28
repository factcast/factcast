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

import io.grpc.stub.StreamObserver;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.TransformationException;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;

/**
 * Bridges GRPC Specific StreamObserver to a subscription by switching over the notification type
 * and dispatching to the appropriate subscription method.
 *
 * @see StreamObserver
 * @see Subscription
 * @author <uwe.schaefer@prisma-capacity.eu>
 */
@RequiredArgsConstructor
@Slf4j
class ClientStreamObserver implements StreamObserver<FactStoreProto.MSG_Notification> {

  final ProtoConverter converter = new ProtoConverter();

  @NonNull final SubscriptionImpl subscription;

  @Override
  public void onNext(MSG_Notification f) {
    log.trace("observer got msg: {}", f);
    switch (f.getType()) {
      case Catchup:
        log.debug("received onCatchup signal");
        subscription.notifyCatchup();
        break;
      case Complete:
        log.debug("received onComplete signal");
        subscription.notifyComplete();
        break;
      case Fact:
        try {
          subscription.notifyElement(converter.fromProto(f.getFact()));
        } catch (TransformationException e) {
          // cannot happen on client side...
          onError(e);
        }
        break;

      default:
        subscription.notifyError(
            new RuntimeException("Unrecognized notification type. THIS IS A BUG!"));
        break;
    }
  }

  @Override
  public void onError(Throwable t) {
    subscription.notifyError(t);
  }

  @Override
  public void onCompleted() {
    subscription.notifyComplete();
  }
}
