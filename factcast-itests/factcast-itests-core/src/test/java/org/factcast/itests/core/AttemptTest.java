package org.factcast.itests.core;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicBoolean;

import org.factcast.core.FactCast;
import org.factcast.core.lock.Attempt;
import org.factcast.core.lock.AttemptAbortedException;
import org.factcast.core.lock.PublishingResult;
import org.factcast.core.spec.FactSpec;
import org.factcast.itests.core.event.UserCreated;
import org.factcast.itests.core.event.UserDeleted;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

@SpringBootTest
@Slf4j
class AttemptTest extends AbstractFactCastIntegrationTest {

  @Autowired FactCast fc;

  @Test
  @SneakyThrows
  void attemptWithEmptyPublish() {
    var factSpecs = FactSpec.from(UserCreated.class, UserDeleted.class);
    final var andThenExecuted = new AtomicBoolean(false);

    PublishingResult publishingResult =
        fc.lock(factSpecs)
            .attempt(() -> Attempt.publish(emptyList()).andThen(() -> andThenExecuted.set(true)));

    assertThat(publishingResult.publishedFacts()).isNotNull().isEmpty();
    // did andThen get executed?
    assertThat(andThenExecuted.get()).isTrue();
  }

  @Test
  @SneakyThrows
  void attemptWithAbortStillThrowsException() {
    var factSpecs = FactSpec.from(UserCreated.class, UserDeleted.class);

    assertThatThrownBy(()-> fc.lock(factSpecs)
            .attempt(() -> Attempt.abort("Error!")))
            .isInstanceOf(AttemptAbortedException.class);

  }

  @Test
  @SneakyThrows
  void neitherPublishNorAbortStillThrowsException() {
    var factSpecs = FactSpec.from(UserCreated.class, UserDeleted.class);

    assertThatThrownBy(()-> fc.lock(factSpecs)
            .attempt(() -> null))
            .isInstanceOf(AttemptAbortedException.class);

  }
}
