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
package org.factcast.factus;

import static org.mockito.Mockito.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.Fact;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.batch.PublishBatch;
import org.factcast.factus.event.EventObject;
import org.factcast.factus.lock.Locked;
import org.factcast.factus.lock.LockedOnSpecs;
import org.factcast.factus.projection.Aggregate;
import org.factcast.factus.projection.ManagedProjection;
import org.factcast.factus.projection.SnapshotProjection;
import org.factcast.factus.projection.SubscribedProjection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FactusTest {

  private final Factus underTest =
      spy(
          new Factus() {
            @Override
            public <T> T publish(@NonNull EventObject e, @NonNull Function<Fact, T> resultFn) {
              return null;
            }

            @Override
            public <T> T publish(
                @NonNull List<EventObject> e, @NonNull Function<List<Fact>, T> resultFn) {
              return null;
            }

            @Override
            public @NonNull PublishBatch batch() {
              return null;
            }

            @Override
            public <P extends SubscribedProjection> Subscription subscribeAndBlock(
                @NonNull P subscribedProjection) {
              return null;
            }

            @Override
            public <P extends SubscribedProjection> Subscription subscribeAndBlock(
                @NonNull P subscribedProjection, @NonNull Duration retryWaitTime) {
              return null;
            }

            @Override
            public <A extends Aggregate> Locked<A> withLockOn(
                @NonNull Class<A> aggregateClass, UUID id) {
              return null;
            }

            @Override
            public <P extends SnapshotProjection> Locked<P> withLockOn(
                @NonNull Class<P> snapshotClass) {
              return null;
            }

            @Override
            public <M extends ManagedProjection> Locked<M> withLockOn(@NonNull M managed) {
              return null;
            }

            @Override
            public Fact toFact(@NonNull EventObject e) {
              return null;
            }

            @Override
            public LockedOnSpecs withLockOn(@NonNull FactSpec spec, FactSpec... additional) {
              return null;
            }

            @Override
            public LockedOnSpecs withLockOn(@NonNull List<FactSpec> specs) {
              return null;
            }

            @Override
            public OptionalLong serialOf(@NonNull UUID factId) {
              return OptionalLong.empty();
            }

            @Override
            public void close() throws IOException {}

            @Override
            public <P extends SnapshotProjection> @NonNull P fetch(
                @NonNull Class<P> projectionClass) {
              return null;
            }

            @Override
            public @NonNull <A extends Aggregate> Optional<A> find(
                @NonNull Class<A> aggregateClass, @NonNull UUID aggregateId) {
              return Optional.empty();
            }

            @Override
            public <P extends ManagedProjection> void update(
                @NonNull P managedProjection, @NonNull Duration maxWaitTime)
                throws TimeoutException {}

            @Override
            public void publish(@NonNull Fact f) {}
          });

  @Test
  void defaultMethod_publishListEventObjects() {
    List<EventObject> l = Lists.newArrayList(new EP(), new EP());
    underTest.publish(l);

    verify(underTest).publish(same(l), any());
  }

  @Test
  void defaultMethod_voidSubscribe() {
    SubscribedProjection p = mock(SubscribedProjection.class);
    underTest.subscribe(p);

    sleep(); // call is async, so we wait a bit

    verify(underTest).subscribeAndBlock(same(p));
  }

  static class SP implements SnapshotProjection {}

  @Test
  void defaultMethod_withLockOnInstance() {
    SP p = new SP();
    underTest.withLockOn(p);
    verify(underTest).withLockOn(SP.class);
  }

  @SneakyThrows
  private void sleep() {
    Thread.sleep(100);
  }

  static class EP implements EventObject {
    @Override
    public Set<UUID> aggregateIds() {
      return Sets.newHashSet();
    }
  }
}
