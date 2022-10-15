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
package org.factcast.itests.exception.handling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.FactValidationException;
import org.factcast.core.lock.Attempt;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.TransformationException;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.factus.Factus;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactcastTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;

@Slf4j
@ContextConfiguration(classes = {Application.class})
@FactcastTestConfig(factcastVersion = "latest")
public class ExceptionHandlingV4 extends AbstractFactCastIntegrationTest {

  @Autowired Factus ec;

  @Autowired FactCast fc;

  @Test
  public void testPublish_validationFailed() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    assertThatThrownBy(
            () ->
                ec.publish(
                    createTestFact(
                        aggId,
                        2,
                        "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\",\"salutation\":\"FOO\"}")))
        .isInstanceOf(FactValidationException.class);
  }

  @Test
  public void testPublish_validationFailed_withLockOn() {
    // INIT
    UUID aggId = UUID.randomUUID();

    // RUN
    assertThatThrownBy(
            () ->
                ec.withLockOn(Collections.singletonList(FactSpec.ns("users")))
                    .attempt(
                        (tx) ->
                            tx.publish(
                                createTestFact(
                                    aggId,
                                    2,
                                    "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\",\"salutation\":\"FOO\"}"))))
        .isInstanceOf(FactValidationException.class);
  }

  @Test
  public void testProjection_transformationErrors() {
    // INIT
    UUID aggId = UUID.randomUUID();

    ec.publish(createTestFact(aggId, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}"));

    var proj = new LocalManagedUserNames();
    assertThatThrownBy(() -> ec.update(proj)).isInstanceOf(TransformationException.class);
  }

  @Test
  public void failingTransformation() {

    UUID id = UUID.randomUUID();
    Fact f = createTestFact(id, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}");
    fc.publish(f);

    assertThatThrownBy(() -> fc.fetchByIdAndVersion(id, 2))
        .isInstanceOf(TransformationException.class);
  }

  @Test
  public void validationFailsOnSchemaViolation() {
    Fact brokenFact = createTestFact(UUID.randomUUID(), 1, "{}");
    assertThatThrownBy(() -> fc.publish(brokenFact)).isInstanceOf(FactValidationException.class);
  }

  @Test
  public void validationFailsOnSchemaViolation_withinLock() {
    Fact brokenFact = createTestFact(UUID.randomUUID(), 1, "{}");

    assertThatThrownBy(
            () -> fc.lock(FactSpec.ns("users")).attempt(() -> Attempt.publish(brokenFact)))
        .isInstanceOf(FactValidationException.class);
  }

  @Test
  @SneakyThrows
  public void testSubscription_transformationErrors_catchup() {
    // INIT
    UUID aggId = UUID.randomUUID();

    ec.publish(createTestFact(aggId, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}"));

    var catchupLatch = new CountDownLatch(1);
    var errorLatch = new CountDownLatch(1);
    var proj = new SubscribedUserNames(catchupLatch, errorLatch);
    ec.subscribe(proj);

    assertThat(errorLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

    assertThat(proj.exception()).isInstanceOf(TransformationException.class);
  }

  @Test
  @SneakyThrows
  public void testSubscription_transformationErrors_follow() {
    // INIT
    UUID aggId = UUID.randomUUID();

    var catchupLatch = new CountDownLatch(1);
    var errorLatch = new CountDownLatch(1);
    var proj = new SubscribedUserNames(catchupLatch, errorLatch);
    ec.subscribe(proj);

    assertThat(catchupLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

    ec.publish(createTestFact(aggId, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}"));

    assertThat(errorLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

    assertThat(proj.exception()).isInstanceOf(TransformationException.class);
  }

  @Test
  @SneakyThrows
  public void testSubscriptionFC_transformationErrors_catchup() {
    // INIT
    UUID aggId = UUID.randomUUID();

    fc.publish(createTestFact(aggId, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}"));

    AtomicReference<Throwable> e = new AtomicReference<>();
    var errorLatch = new CountDownLatch(1);

    fc.subscribe(
        SubscriptionRequest.follow(FactSpec.ns("users").type("UserCreated").version(2))
            .fromScratch(),
        new FactObserver() {
          @Override
          public void onNext(@NonNull Fact element) {}

          @Override
          public void onError(@NonNull Throwable exception) {
            e.set(exception);
            errorLatch.countDown();
            FactObserver.super.onError(exception);
          }
        });

    assertThat(errorLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

    assertThat(e.get()).isInstanceOf(TransformationException.class);
  }

  @Test
  @SneakyThrows
  public void testSubscriptionFC_transformationErrors_follow() {
    // INIT
    UUID aggId = UUID.randomUUID();

    AtomicReference<Throwable> e = new AtomicReference<>();
    var errorLatch = new CountDownLatch(1);
    var catchupLatch = new CountDownLatch(1);

    fc.subscribe(
        SubscriptionRequest.follow(FactSpec.ns("users").type("UserCreated").version(2))
            .fromScratch(),
        new FactObserver() {
          @Override
          public void onNext(@NonNull Fact element) {}

          @Override
          public void onCatchup() {
            catchupLatch.countDown();
            FactObserver.super.onCatchup();
          }

          @Override
          public void onError(@NonNull Throwable exception) {
            e.set(exception);
            errorLatch.countDown();
            FactObserver.super.onError(exception);
          }
        });

    assertThat(catchupLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

    fc.publish(createTestFact(aggId, 1, "{\"firstName\":\"Peter\",\"lastName\":\"Zwegert\"}"));

    assertThat(errorLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue();

    assertThat(e.get()).isInstanceOf(TransformationException.class);
  }

  private Fact createTestFact(UUID id, int version, String body) {
    return Fact.builder().ns("users").type("UserCreated").id(id).version(version).build(body);
  }
}
