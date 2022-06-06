package org.factcast.itests.factus;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.subscription.Subscription;
import org.factcast.factus.Factus;
import org.factcast.itests.factus.proj.SubscribedUserNames;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;

@SpringBootTest
// @ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@DirtiesContext(classMode = ClassMode.BEFORE_EACH_TEST_METHOD)
@Slf4j
public class LocalSubscribedLockManagementTest extends AbstractFactCastIntegrationTest {
  @Autowired Factus factus;

  @SneakyThrows
  @Test
  void releasesLockOnClose() {

    SubscribedUserNames p = Mockito.spy(new SubscribedUserNames());

    Subscription s = factus.subscribeAndBlock(p);

    CompletableFuture.runAsync(
            () -> {
              Assertions.assertThrows(
                  Exception.class, () -> p.acquireWriteToken(Duration.ofMillis(300)));
            })
        .get();
  }
}
