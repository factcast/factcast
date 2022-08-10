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
package org.factcast.store.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.stream.*;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.factcast.core.DuplicateFactException;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.lock.Attempt;
import org.factcast.core.lock.AttemptAbortedException;
import org.factcast.core.lock.ExceptionAfterPublish;
import org.factcast.core.lock.PublishingResult;
import org.factcast.core.lock.WithOptimisticLock.OptimisticRetriesExceededException;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.annotation.DirtiesContext;

@SuppressWarnings("deprecation")
public abstract class AbstractFactStoreTest {

  static final FactSpec ANY = FactSpec.ns("default");

  protected FactCast uut;

  protected FactStore store;

  @BeforeEach
  void setUp() {
    store = spy(createStoreToTest());
    uut = spy(FactCast.from(store));
  }

  protected abstract FactStore createStoreToTest();

  @Test
  public void testFetchById() {
    UUID id = UUID.randomUUID();
    uut.fetchById(id);
    verify(store).fetchById(id);
  }

  @Test
  public void testFetchByIdAndVersion() {
    UUID id = UUID.randomUUID();
    uut.fetchByIdAndVersion(id, 77);
    verify(store).fetchByIdAndVersion(id, 77);
  }

  @Test
  public void testPublishNullParameter() {
    assertThrows(NullPointerException.class, () -> createStoreToTest().publish(null));
  }

  @DirtiesContext
  @Test
  protected void testEmptyStore() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          FactObserver observer = mock(FactObserver.class);
          Subscription s = uut.subscribe(SubscriptionRequest.catchup(ANY).fromScratch(), observer);
          s.awaitComplete();
          verify(observer).onCatchup();
          verify(observer).onComplete();
          verify(observer, never()).onError(any());
          verify(observer, never()).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testUniquenessConstraint() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          Assertions.assertThrows(
              DuplicateFactException.class,
              () -> {
                UUID id = UUID.randomUUID();
                uut.publish(
                    Fact.of(
                        "{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
                uut.publish(
                    Fact.of(
                        "{\"id\":\"" + id + "\",\"type\":\"someType\",\"ns\":\"default\"}", "{}"));
                fail();
              });
        });
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreFollowNonMatching() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          TestFactObserver observer = testObserver();
          uut.subscribe(SubscriptionRequest.follow(ANY).fromScratch(), observer).awaitCatchup();
          verify(observer).onCatchup();
          verify(observer, never()).onComplete();
          verify(observer, never()).onError(any());
          verify(observer, never()).onNext(any());
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          observer.await(2);
          // the mark facts only
          verify(observer, times(2)).onNext(any());
        });
  }

  private TestFactObserver testObserver() {
    return spy(new TestFactObserver());
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreFollowMatching() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          TestFactObserver observer = testObserver();
          uut.subscribe(SubscriptionRequest.follow(ANY).fromScratch(), observer).awaitCatchup();
          verify(observer).onCatchup();
          verify(observer, never()).onComplete();
          verify(observer, never()).onError(any());
          verify(observer, never()).onNext(any());
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          observer.await(1);
        });
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreEphemeral() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          TestFactObserver observer = testObserver();
          uut.subscribe(SubscriptionRequest.follow(ANY).fromNowOn(), observer).awaitCatchup();
          // nothing recieved
          verify(observer).onCatchup();
          verify(observer, never()).onComplete();
          verify(observer, never()).onError(any());
          verify(observer, never()).onNext(any());
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          observer.await(1);
          verify(observer, times(1)).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreEphemeralWithCancel() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          TestFactObserver observer = testObserver();
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          Subscription subscription =
              uut.subscribe(SubscriptionRequest.follow(ANY).fromNowOn(), observer).awaitCatchup();
          // nothing recieved
          verify(observer).onCatchup();
          verify(observer, never()).onComplete();
          verify(observer, never()).onError(any());
          verify(observer, never()).onNext(any());
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          observer.await(1);
          verify(observer, times(1)).onNext(any());
          subscription.close();
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          Thread.sleep(100);
          // additional event not received
          verify(observer, times(1)).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreFollowWithCancel() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          TestFactObserver observer = testObserver();
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          Subscription subscription =
              uut.subscribe(SubscriptionRequest.follow(ANY).fromScratch(), observer).awaitCatchup();
          // nothing recieved
          verify(observer).onCatchup();
          verify(observer, never()).onComplete();
          verify(observer, never()).onError(any());
          verify(observer, times(3)).onNext(any());
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          observer.await(4);
          subscription.close();
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          Thread.sleep(100);
          // additional event not received
          verify(observer, times(4)).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreCatchupMatching() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          FactObserver observer = mock(FactObserver.class);
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.subscribe(SubscriptionRequest.catchup(ANY).fromScratch(), observer).awaitComplete();
          verify(observer).onCatchup();
          verify(observer).onComplete();
          verify(observer, never()).onError(any());
          verify(observer).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreFollowMatchingDelayed() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          TestFactObserver observer = testObserver();
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          uut.subscribe(SubscriptionRequest.follow(ANY).fromScratch(), observer).awaitCatchup();
          verify(observer).onCatchup();
          verify(observer, never()).onComplete();
          verify(observer, never()).onError(any());
          verify(observer).onNext(any());
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\",\"ns\":\"default\"}",
                  "{}"));
          observer.await(2);
        });
  }

  @DirtiesContext
  @Test
  protected void testEmptyStoreFollowNonMatchingDelayed() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          TestFactObserver observer = testObserver();
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"default\",\"type\":\"t1\"}",
                  "{}"));
          uut.subscribe(SubscriptionRequest.follow(ANY).fromScratch(), observer).awaitCatchup();
          verify(observer).onCatchup();
          verify(observer, never()).onComplete();
          verify(observer, never()).onError(any());
          verify(observer).onNext(any());
          uut.publish(
              Fact.of(
                  "{\"id\":\"" + UUID.randomUUID() + "\",\"ns\":\"other\",\"type\":\"t1\"}", "{}"));
          observer.await(1);
        });
  }

  @DirtiesContext
  @Test
  protected void testRequiredMetaAttribute() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          FactObserver observer = mock(FactObserver.class);
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                  "{}"));
          FactSpec REQ_FOO_BAR = FactSpec.ns("default").meta("foo", "bar");
          uut.subscribe(SubscriptionRequest.catchup(REQ_FOO_BAR).fromScratch(), observer)
              .awaitComplete();
          verify(observer).onFactStreamInfo(any());
          verify(observer).onNext(any());
          verify(observer).onCatchup();
          verify(observer).onComplete();
          verifyNoMoreInteractions(observer);
        });
  }

  @DirtiesContext
  @Test
  protected void testScriptedWithPayloadFiltering() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          FactObserver observer = mock(FactObserver.class);
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                  "{}"));
          FactSpec SCRIPTED =
              FactSpec.ns("default").jsFilterScript("function (h,e){ return (h.hit=='me')}");
          uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
              .awaitComplete();
          verify(observer).onFactStreamInfo(any());
          verify(observer).onNext(any());
          verify(observer).onCatchup();
          verify(observer).onComplete();
          verifyNoMoreInteractions(observer);
        });
  }

  @DirtiesContext
  @Test
  protected void testScriptedWithHeaderFiltering() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          FactObserver observer = mock(FactObserver.class);
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                  "{}"));
          FactSpec SCRIPTED =
              FactSpec.ns("default").jsFilterScript("function (h){ return (h.hit=='me')}");
          uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
              .awaitComplete();
          verify(observer).onFactStreamInfo(any());
          verify(observer).onNext(any());
          verify(observer).onCatchup();
          verify(observer).onComplete();
          verifyNoMoreInteractions(observer);
        });
  }

  @DirtiesContext
  @Test
  protected void testScriptedFilteringMatchAll() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          FactObserver observer = mock(FactObserver.class);
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                  "{}"));
          FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h){ return true }");
          uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
              .awaitComplete();
          verify(observer).onFactStreamInfo(any());
          verify(observer, times(2)).onNext(any());
          verify(observer).onCatchup();
          verify(observer).onComplete();
          verifyNoMoreInteractions(observer);
        });
  }

  @DirtiesContext
  @Test
  protected void testScriptedFilteringMatchNone() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          FactObserver observer = mock(FactObserver.class);
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"hit\":\"me\"}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + UUID.randomUUID()
                      + "\",\"ns\":\"default\",\"type\":\"noone_knows\",\"meta\":{\"foo\":\"bar\"}}",
                  "{}"));
          FactSpec SCRIPTED = FactSpec.ns("default").jsFilterScript("function (h){ return false }");
          uut.subscribe(SubscriptionRequest.catchup(SCRIPTED).fromScratch(), observer)
              .awaitComplete();
          verify(observer).onFactStreamInfo(any());
          verify(observer).onCatchup();
          verify(observer).onComplete();
          verifyNoMoreInteractions(observer);
        });
  }

  @DirtiesContext
  @Test
  protected void testMatchBySingleAggId() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          UUID id = UUID.randomUUID();
          UUID aggId1 = UUID.randomUUID();
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + id
                      + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                      + aggId1
                      + "\"]}",
                  "{}"));
          FactObserver observer = mock(FactObserver.class);
          uut.subscribe(
                  SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1)).fromScratch(),
                  observer)
              .awaitComplete();
          verify(observer, times(1)).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testMatchByOneOfAggId() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          UUID id = UUID.randomUUID();
          UUID aggId1 = UUID.randomUUID();
          UUID aggId2 = UUID.randomUUID();
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + id
                      + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                      + aggId1
                      + "\",\""
                      + aggId2
                      + "\"]}",
                  "{}"));
          FactObserver observer = mock(FactObserver.class);
          uut.subscribe(
                  SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId1)).fromScratch(),
                  observer)
              .awaitComplete();
          verify(observer, times(1)).onNext(any());
          observer = mock(FactObserver.class);
          uut.subscribe(
                  SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2)).fromScratch(),
                  observer)
              .awaitComplete();
          verify(observer, times(1)).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testMatchBySecondAggId() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          UUID id = UUID.randomUUID();
          UUID aggId1 = UUID.randomUUID();
          UUID aggId2 = UUID.randomUUID();
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + id
                      + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                      + aggId1
                      + "\",\""
                      + aggId2
                      + "\"]}",
                  "{}"));
          FactObserver observer = mock(FactObserver.class);
          uut.subscribe(
                  SubscriptionRequest.catchup(FactSpec.ns("default").aggId(aggId2)).fromScratch(),
                  observer)
              .awaitComplete();
          verify(observer, times(1)).onNext(any());
        });
  }

  @DirtiesContext
  @Test
  protected void testDelayed() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          UUID id = UUID.randomUUID();
          TestFactObserver obs = new TestFactObserver();
          try (Subscription s =
              uut.subscribe(
                  SubscriptionRequest.follow(500, FactSpec.ns("default").aggId(id)).fromScratch(),
                  obs)) {
            uut.publish(
                Fact.of(
                    "{\"id\":\""
                        + id
                        + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                        + id
                        + "\"]}",
                    "{}"));
            // will take some time on pgstore
            obs.await(1);
          }
        });
  }

  @DirtiesContext
  @Test
  protected void testSerialOf() {
    Assertions.assertTimeout(
        Duration.ofMillis(30000),
        () -> {
          UUID id = UUID.randomUUID();
          UUID id2 = UUID.randomUUID();
          assertFalse(uut.serialOf(id).isPresent());
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + id
                      + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                      + id
                      + "\"]}",
                  "{}"));
          uut.publish(
              Fact.of(
                  "{\"id\":\""
                      + id2
                      + "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\""
                      + id
                      + "\"]}",
                  "{}"));
          long ser1 = uut.serialOf(id).getAsLong();
          long ser2 = uut.serialOf(id2).getAsLong();
          assertTrue(ser1 < ser2);
        });
  }

  // TODO: implement alternative
  /*
   * @DirtiesContext
   *
   * @Test protected void testSerialHeader() {
   * Assertions.assertTimeout(Duration.ofMillis(30000), () -> { UUID id =
   * UUID.randomUUID(); uut.publish(Fact.of( "{\"id\":\"" + id +
   * "\",\"type\":\"someType\",\"ns\":\"default\",\"aggIds\":[\"" + id +
   * "\"]}", "{}")); UUID id2 = UUID.randomUUID();
   * uut.publish(Fact.of("{\"id\":\"" + id2 +
   * "\",\"type\":\"someType\",\"meta\":{\"foo\":\"bar\"},\"ns\":\"default\",\"aggIds\":[\""
   * + id2 + "\"]}", "{}")); OptionalLong serialOf = uut.serialOf(id);
   * assertTrue(serialOf.isPresent()); Fact f = uut.fetchById(id).get(); Fact
   * fact2 = uut.fetchById(id2).get(); assertEquals(serialOf.getAsLong(),
   * f.serial()); assertTrue(f.before(fact2)); }); }
   */

  @Test
  protected void testChecksMandatoryNamespaceOnPublish() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () ->
            uut.publish(
                Fact.of("{\"id\":\"" + UUID.randomUUID() + "\",\"type\":\"someType\"}", "{}")));
  }

  @Test
  protected void testChecksMandatoryIdOnPublish() {
    Assertions.assertThrows(
        IllegalArgumentException.class,
        () -> uut.publish(Fact.of("{\"ns\":\"default\",\"type\":\"someType\"}", "{}")));
  }

  @Test
  protected void testEnumerateNameSpaces() {
    // no namespaces
    assertEquals(0, uut.enumerateNamespaces().size());
    uut.publish(Fact.builder().ns("ns1").type("type").build("{}"));
    uut.publish(Fact.builder().ns("ns2").type("type").build("{}"));
    Set<String> ns = uut.enumerateNamespaces();
    assertEquals(2, ns.size());
    assertTrue(ns.contains("ns1"));
    assertTrue(ns.contains("ns2"));
  }

  @Test
  protected void testEnumerateTypes() {
    uut.publish(Fact.builder().ns("ns1").type("t1").build("{}"));
    uut.publish(Fact.builder().ns("ns2").type("t2").build("{}"));
    assertEquals(1, uut.enumerateTypes("ns1").size());
    assertTrue(uut.enumerateTypes("ns1").contains("t1"));
    uut.publish(Fact.builder().ns("ns1").type("t1").build("{}"));
    uut.publish(Fact.builder().ns("ns1").type("t1").build("{}"));
    uut.publish(Fact.builder().ns("ns1").type("t2").build("{}"));
    uut.publish(Fact.builder().ns("ns1").type("t3").build("{}"));
    uut.publish(Fact.builder().ns("ns1").type("t2").build("{}"));
    assertEquals(3, uut.enumerateTypes("ns1").size());
    assertTrue(uut.enumerateTypes("ns1").contains("t1"));
    assertTrue(uut.enumerateTypes("ns1").contains("t2"));
    assertTrue(uut.enumerateTypes("ns1").contains("t3"));
  }

  @Test
  protected void testFollow() throws Exception {
    uut.publish(newFollowTestFact());
    AtomicReference<CountDownLatch> l = new AtomicReference<>(new CountDownLatch(1));
    SubscriptionRequest request =
        SubscriptionRequest.follow(FactSpec.ns("followtest")).fromScratch();
    FactObserver observer = element -> l.get().countDown();
    uut.subscribe(request, observer);
    // make sure, you got the first one
    assertTrue(l.get().await(1500, TimeUnit.MILLISECONDS));

    l.set(new CountDownLatch(3));
    uut.publish(newFollowTestFact());
    uut.publish(newFollowTestFact());
    // needs to fail
    assertFalse(l.get().await(1500, TimeUnit.MILLISECONDS));
    assertEquals(1, l.get().getCount());

    uut.publish(newFollowTestFact());

    assertTrue(
        l.get().await(10, TimeUnit.SECONDS),
        "failed to see all the facts published within 10 seconds.");
  }

  @Test
  protected void testCatchup() throws Exception {
    String ns = "catchuptest";
    uut.publish(newTestFact(ns));
    AtomicReference<UUID> last = new AtomicReference<>();

    // fetch all there is from scratch
    SubscriptionRequest request = SubscriptionRequest.catchup(FactSpec.ns(ns)).fromScratch();
    FactObserver observer = element -> last.set(element.id());
    uut.subscribe(request, observer).awaitComplete();

    // now we should have the published one in last
    assertNotNull(last.get());

    // catchup from last, should not bring anything new.
    request = SubscriptionRequest.catchup(FactSpec.ns(ns)).from(last.get());
    observer =
        element -> {
          System.out.println("unexpected fact recieved");
          fail();
        };
    uut.subscribe(request, observer).awaitComplete();

    // now, add two more
    uut.publish(newTestFact(ns));
    uut.publish(newTestFact(ns));

    // and catchup from the last recorded should bring exactly two
    CountDownLatch expectingTwo = new CountDownLatch(2);
    request = SubscriptionRequest.catchup(FactSpec.ns(ns)).from(last.get());
    observer =
        element -> {
          expectingTwo.countDown();
          if (element.id().equals(last.get())) {
            System.out.println("duplicate fact recieved");
            fail();
          }
        };
    uut.subscribe(request, observer);
    assertTrue(expectingTwo.await(2, TimeUnit.SECONDS));

    // apparently, all fine

  }

  private Fact newTestFact(String ns) {
    return Fact.builder().ns(ns).id(UUID.randomUUID()).type("type").build("{}");
  }

  private Fact newFollowTestFact() {
    return newTestFact("followtest");
  }

  @Test
  public void testSubscribeStartingAfter() throws Exception {
    for (int i = 0; i < 10; i++) {
      uut.publish(Fact.builder().id(new UUID(0L, i)).ns("ns1").type("t1").build("{}"));
    }

    ToListObserver toListObserver = new ToListObserver();

    SubscriptionRequest request =
        SubscriptionRequest.catchup(FactSpec.ns("ns1")).from(new UUID(0L, 7L));
    Subscription s = uut.subscribe(request, toListObserver);
    s.awaitComplete();

    assertEquals(2, toListObserver.list().size());
  }

  /// optimistic locking

  final String NS = "ns1";

  private Fact fact(UUID aggId) {
    return Fact.builder().ns(NS).type("t1").aggId(aggId).build("{}");
  }

  private List<Fact> catchup() {
    return catchup(FactSpec.ns(NS));
  }

  private List<Fact> catchup(FactSpec s) {

    LinkedList<Fact> l = new LinkedList<>();
    FactObserver o = l::add;
    uut.subscribe(SubscriptionRequest.catchup(s).fromScratch(), o).awaitCatchup();
    return l;
  }

  @Test
  void happyPath() throws Exception {

    // setup
    UUID agg1 = UUID.randomUUID();
    uut.publish(fact(agg1));

    PublishingResult ret =
        uut.lock(NS)
            .on(agg1)
            .attempt(
                () -> {
                  return Attempt.publish(fact(agg1));
                });

    verify(store).publishIfUnchanged(any(), any());
    assertThat(catchup()).hasSize(2);
    assertThat(ret).isNotNull();
  }

  @Test
  void happyPathWithEmptyStore() throws Exception {

    // setup
    UUID agg1 = UUID.randomUUID();

    PublishingResult ret =
        uut.lock(NS)
            .on(agg1)
            .attempt(
                () -> {
                  return Attempt.publish(fact(agg1));
                });

    verify(store).publishIfUnchanged(any(), any());
    assertThat(catchup()).hasSize(1);
    assertThat(ret).isNotNull();
  }

  @Test
  void npeOnNamespaceMissing() throws Exception {
    assertThrows(NullPointerException.class, () -> uut.lock((List<FactSpec>) null));
  }

  @Test
  void npeOnNamespaceEmpty() throws Exception {
    assertThrows(IllegalArgumentException.class, () -> uut.lock(""));
  }

  @Test
  void npeOnAggIdMissing() throws Exception {
    assertThrows(NullPointerException.class, () -> uut.lock("foo").on(null));
  }

  @Test
  void npeOnAttemptIsNull() throws Exception {
    assertThrows(
        NullPointerException.class, () -> uut.lock("foo").on(UUID.randomUUID()).attempt(null));
  }

  @Test
  void abortOnAttemptReturningNull() throws Exception {
    assertThrows(
        AttemptAbortedException.class,
        () -> uut.lock("foo").on(UUID.randomUUID()).attempt(() -> null));
  }

  @Test
  void happyPathWithGlobalLock() throws Exception {

    // setup
    UUID agg1 = UUID.randomUUID();
    UUID agg2 = UUID.randomUUID();
    uut.publish(fact(agg1));

    PublishingResult ret =
        uut.lock(NS)
            .on(agg1, agg2)
            .attempt(
                () -> {
                  return Attempt.publish(fact(agg1));
                });

    verify(store).publishIfUnchanged(any(), any());
    assertThat(catchup()).hasSize(2);
    assertThat(ret).isNotNull();
  }

  @Test
  void happyPathWithMoreThanOneAggregate() throws Exception {

    // setup
    UUID agg1 = UUID.randomUUID();
    UUID agg2 = UUID.randomUUID();
    uut.publish(fact(agg1));

    PublishingResult ret =
        uut.lock(NS)
            .on(agg1, agg2)
            .attempt(
                () -> {
                  return Attempt.publish(fact(agg1));
                });

    verify(store).publishIfUnchanged(any(), any());
    assertThat(catchup()).hasSize(2);
    assertThat(ret).isNotNull();
  }

  @Test
  void happyPathWithMoreThanOneAggregateAndRetry() throws Exception {

    // setup
    UUID agg1 = UUID.randomUUID();
    UUID agg2 = UUID.randomUUID();
    uut.publish(fact(agg1));
    uut.publish(fact(agg2));

    CountDownLatch c = new CountDownLatch(8);

    PublishingResult ret =
        uut.lock(NS)
            .on(agg1, agg2)
            .optimistic()
            .retry(100)
            .attempt(
                () -> {
                  if (c.getCount() > 0) {
                    c.countDown();

                    if (Math.random() < 0.5) {
                      uut.publish(fact(agg1));
                    } else {
                      uut.publish(fact(agg2));
                    }
                  }

                  return Attempt.publish(fact(agg2));
                });

    assertThat(catchup()).hasSize(11); // 8 conflicting, 2 initial and 1
    // from Attempt
    assertThat(ret).isNotNull();

    // publishing was properly blocked
    assertThat(c.getCount()).isEqualTo(0);
  }

  @Test
  void happyPathWithGlobalLockAndRetry() throws Exception {

    // setup
    UUID agg1 = UUID.randomUUID();
    UUID agg2 = UUID.randomUUID();
    uut.publish(fact(agg1));
    uut.publish(fact(agg2));

    CountDownLatch c = new CountDownLatch(8);

    PublishingResult ret =
        uut.lock(NS)
            .on(agg1, agg2)
            .optimistic()
            .retry(100)
            .attempt(
                () -> {
                  if (c.getCount() > 0) {
                    c.countDown();

                    if (Math.random() < 0.5) {
                      uut.publish(fact(agg1));
                    } else {
                      uut.publish(fact(agg2));
                    }
                  }

                  return Attempt.publish(fact(agg2));
                });

    assertThat(catchup()).hasSize(11); // 8 conflicting, 2 initial and 1
    // from Attempt
    assertThat(ret).isNotNull();

    // publishing was properly blocked
    assertThat(c.getCount()).isEqualTo(0);
  }

  @Test
  void shouldThrowAttemptAbortedException() {
    assertThrows(
        AttemptAbortedException.class,
        () -> uut.lock(NS).on(UUID.randomUUID()).attempt(() -> Attempt.abort("don't want to")));
  }

  @Test
  void shouldWrapExceptionIntoAttemptAbortedException() {
    assertThrows(
        AttemptAbortedException.class,
        () ->
            uut.lock(NS)
                .on(UUID.randomUUID())
                .attempt(
                    () -> {
                      throw new UnsupportedOperationException();
                    }));
  }

  @Test
  void shouldNotExecuteAndThenDueToAbort() {

    Runnable e = mock(Runnable.class);

    assertThrows(
        AttemptAbortedException.class,
        () ->
            uut.lock(NS)
                .on(UUID.randomUUID())
                .attempt(() -> Attempt.abort("don't want to").andThen(e)));

    verify(e, never()).run();
  }

  @Test
  void shouldExecuteAndThen()
      throws OptimisticRetriesExceededException, ExceptionAfterPublish, AttemptAbortedException {

    Runnable e = mock(Runnable.class);

    uut.lock(NS)
        .on(UUID.randomUUID())
        .attempt(() -> Attempt.publish(fact(UUID.randomUUID())).andThen(e));

    verify(e).run();
  }

  @Test
  void shouldThrowCorrectExceptionOnFailureOfAndThen()
      throws OptimisticRetriesExceededException, ExceptionAfterPublish, AttemptAbortedException {

    Runnable e = mock(Runnable.class);
    Mockito.doThrow(NumberFormatException.class).when(e).run();

    assertThrows(
        ExceptionAfterPublish.class,
        () ->
            uut.lock(NS)
                .on(UUID.randomUUID())
                .attempt(() -> Attempt.publish(fact(UUID.randomUUID())).andThen(e)));
    verify(e).run();
  }

  @Test
  void shouldExecuteAndThenOnlyOnce()
      throws OptimisticRetriesExceededException, ExceptionAfterPublish, AttemptAbortedException {

    Runnable e = mock(Runnable.class);
    CountDownLatch c = new CountDownLatch(5);
    UUID agg1 = UUID.randomUUID();

    uut.lock(NS)
        .on(agg1)
        .attempt(
            () -> {
              c.countDown();
              if (c.getCount() > 0) {
                Fact conflictingFact = fact(agg1);
                uut.publish(conflictingFact);
              }
              return Attempt.publish(fact(agg1)).andThen(e);
            });

    // there were many attempts
    assertThat(c.getCount()).isEqualTo(0);
    // but andThen is called only once
    verify(e, times(1)).run();
  }

  @Test
  void shouldThrowAttemptAbortedException_withMessage() {
    try {
      uut.lock(NS).on(UUID.randomUUID()).attempt(() -> Attempt.abort("don't want to"));
      fail("should not have gotten here");
    } catch (AttemptAbortedException e) {
      assertThat(e.getMessage()).isEqualTo("don't want to");
    }
  }

  @Getter
  class MyAbortException extends AttemptAbortedException {

    private static final long serialVersionUID = 1L;

    private final int i;

    public MyAbortException(int i) {
      super("nah");
      this.i = i;
    }
  }

  @Test
  void shouldPassCustomAbortedException() {

    UUID agg1 = UUID.randomUUID();

    try {
      uut.lock(NS)
          .on(agg1)
          .attempt(
              () -> {
                throw new MyAbortException(42);
              });
      fail("should not have gotten here");
    } catch (AttemptAbortedException e) {
      assertThat(e.getMessage()).isEqualTo("nah");
      assertThat(e).isInstanceOf(MyAbortException.class);
      assertThat(((MyAbortException) e).i()).isEqualTo(42);
    }
  }

  @Test
  void shouldNotBeBlockedByUnrelatedFact() throws Exception {

    UUID agg1 = UUID.randomUUID();
    UUID agg2 = UUID.randomUUID();

    uut.lock(NS)
        .on(agg1)
        .attempt(
            () -> {

              // write unrelated fact first
              uut.publish(fact(agg2));

              return Attempt.publish(fact(agg1));
            });

    assertThat(catchup()).hasSize(2);
  }

  @Test
  void shouldThrowRetriesExceededException() {

    UUID agg1 = UUID.randomUUID();

    assertThrows(
        OptimisticRetriesExceededException.class,
        () ->
            uut.lock(NS)
                .on(agg1)
                .attempt(
                    () -> {

                      // write conflicting fact first
                      uut.publish(fact(agg1));

                      return Attempt.publish(fact(agg1));
                    }));
  }

  @Test
  void shouldReturnIdOfLastFactPublished() throws Exception {

    UUID agg1 = UUID.randomUUID();

    UUID expected = UUID.randomUUID();

    PublishingResult ret =
        uut.lock(NS)
            .on(agg1)
            .attempt(
                () -> {
                  Fact lastFact = Fact.builder().ns(NS).id(expected).build("{}");
                  return Attempt.publish(fact(agg1), fact(agg1), fact(agg1), lastFact);
                });

    List<Fact> all = catchup();
    assertThat(all).hasSize(4);
    assertThat(all.get(all.size() - 1).id()).isEqualTo(expected);
    assertThat(
            ret.publishedFacts().stream()
                .map(Fact::id)
                .collect(Collectors.toList())
                .contains(expected))
        .isTrue();
  }

  @Test
  void shouldReleaseTokenOnAbort() {

    UUID agg1 = UUID.randomUUID();

    try {
      uut.lock(NS).on(agg1).attempt(() -> Attempt.abort("narf"));
    } catch (AttemptAbortedException expected) {
    }

    verify(store, times(1)).currentStateFor(any());
    verify(store, times(1)).invalidate(any());
  }

  @Test
  void shouldNotReleaseTokenOnPublish() throws Exception {

    // token is already released by publishIfUnchanged

    UUID agg1 = UUID.randomUUID();

    uut.lock(NS).on(agg1).attempt(() -> Attempt.publish(fact(agg1)));

    verify(store, times(1)).currentStateFor(any());
    verify(store, times(0)).invalidate(any());
  }

  @Test
  void shouldReleaseTokenOnEmptyPublications() {

    UUID agg1 = UUID.randomUUID();

    try {
      uut.lock(NS).on(agg1).attempt(() -> null);
    } catch (AttemptAbortedException expected) {
    }

    verify(store, times(1)).currentStateFor(any());
    verify(store, times(1)).invalidate(any());
  }

  static class ToListObserver implements FactObserver {
    @Getter private final List<Fact> list = new LinkedList<>();

    @Override
    public void onNext(@NonNull Fact element) {
      list.add(element);
    }
  }

  private static class TestFactObserver implements FactObserver {

    private final List<Fact> values = new CopyOnWriteArrayList<>();

    @Override
    public void onNext(Fact element) {
      values.add(element);
    }

    @SneakyThrows
    public void await(int count) {
      while (true) {
        if (values.size() >= count) {
          return;
        } else {
          Thread.sleep(50);
        }
      }
    }
  }

  @Test
  public void testCurrentTimeProgresses() throws Exception {

    long t1 = store.currentTime();
    Thread.sleep(50);
    long t2 = store.currentTime();

    assertNotEquals(t1, t2);

    assertTrue(Math.abs(t1 - t2) < 200);
    assertTrue(Math.abs(t1 - t2) > 40);
  }
}
