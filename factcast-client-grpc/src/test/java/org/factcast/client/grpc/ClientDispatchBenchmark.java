/*
 * Copyright © 2017-2026 factcast.org
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

import java.util.*;
import org.factcast.core.Fact;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.grpc.api.gen.FactStoreProto.MSG_Notification;
import org.openjdk.jmh.annotations.*;

/** Measures client callback delivery without network or database work. */
@State(Scope.Thread)
public class ClientDispatchBenchmark {

  @Param({"1", "100"})
  public int factsPerNotification;

  private ClientStreamObserver client;
  private SubscriptionImpl subscription;
  private MSG_Notification notification;
  private long delivered;

  @Setup(Level.Trial)
  public void setUp() {
    FactObserver sink = fact -> delivered++;
    subscription = SubscriptionImpl.on(sink);
    client = new ClientStreamObserver(subscription, 0L);
    List<Fact> facts = new ArrayList<>(factsPerNotification);
    for (int i = 0; i < factsPerNotification; i++) {
      facts.add(
          Fact.of(
              "{\"ns\":\"bench\",\"id\":\"" + UUID.randomUUID() + "\"}", "{\"value\":" + i + "}"));
    }
    notification = new ProtoConverter().createNotificationFor(facts);
  }

  @TearDown(Level.Trial)
  public void tearDown() {
    subscription.close();
  }

  @Benchmark
  public long deliver() {
    client.onNext(notification);
    return delivered;
  }
}
