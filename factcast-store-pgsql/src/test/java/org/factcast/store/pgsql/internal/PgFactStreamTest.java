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
package org.factcast.store.pgsql.internal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.eventbus.EventBus;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.factcast.core.subscription.SubscriptionImpl;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.store.pgsql.internal.catchup.PgCatchupFactory;
import org.factcast.store.pgsql.internal.query.PgFactIdToSerialMapper;
import org.factcast.store.pgsql.internal.query.PgLatestSerialFetcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
public class PgFactStreamTest {

  @InjectMocks PgFactStream uut;

  @Test
  public void testConnectNullParameter() {
    assertThrows(NullPointerException.class, () -> uut.connect(null));
  }

  @SuppressWarnings({"unused", "UnstableApiUsage"})
  @Nested
  class FastForward {

    @Mock JdbcTemplate jdbcTemplate;

    @Mock EventBus eventBus;

    @Mock PgFactIdToSerialMapper idToSerMapper;

    @Mock SubscriptionImpl subscription;

    @Mock final AtomicLong serial = new AtomicLong(0);

    @Mock final AtomicBoolean disconnected = new AtomicBoolean(false);

    @Mock PgLatestSerialFetcher fetcher;

    @Mock PgCatchupFactory pgCatchupFactory;
    @Mock FastForwardTarget ffwdTarget;
    @Mock SubscriptionRequest request;
    @InjectMocks PgFactStream underTest;

    @Test
    void noFfwdNotConnected() {

      underTest.close();
      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdFromScratch() {
      when(request.startingAfter()).thenReturn(Optional.empty());

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdIfNoTarget() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(null);

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdIfFactsHaveBeenSent() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(UUID.randomUUID());
      serial.set(100);

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void noFfwdIfTargetBehind() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      when(ffwdTarget.targetId()).thenReturn(UUID.randomUUID());
      when(ffwdTarget.targetSer()).thenReturn(9L);

      underTest.fastForward(request, subscription);

      verifyNoInteractions(subscription);
    }

    @Test
    void ffwdIfTargetAhead() {
      UUID uuid = UUID.randomUUID();
      when(request.startingAfter()).thenReturn(Optional.of(uuid));
      when(idToSerMapper.retrieve(uuid)).thenReturn(10L);
      UUID target = UUID.randomUUID();
      when(ffwdTarget.targetId()).thenReturn(target);
      when(ffwdTarget.targetSer()).thenReturn(90L);

      underTest.fastForward(request, subscription);

      verify(subscription).notifyFastForward(target);
    }
  }
}
