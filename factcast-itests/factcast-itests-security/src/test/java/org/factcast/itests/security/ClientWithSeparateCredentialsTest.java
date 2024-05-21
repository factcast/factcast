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
package org.factcast.itests.security;

import static org.assertj.core.api.Assertions.*;

import io.grpc.Channel;
import io.grpc.StatusRuntimeException;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.channelfactory.GrpcChannelFactory;
import org.factcast.client.grpc.FactCastGrpcStubsFactory;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.Subscription;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.factcast.grpc.api.conv.ProtoConverter;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.factcast.test.FactcastTestConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@ContextConfiguration(classes = {TestApplication.class})
@TestPropertySource(locations = "/application-separate-creds.properties")
@Slf4j
@FactcastTestConfig(securityEnabled = true)
public class ClientWithSeparateCredentialsTest extends AbstractFactCastIntegrationTest {
  @Autowired FactCast fc;

  @Autowired
  FactCastGrpcStubsFactory stubsFactory;

  @Autowired
  GrpcChannelFactory channelFactory;

  @Autowired
  JdbcTemplate jdbcTemplate;

  private final ProtoConverter converter = new ProtoConverter();

  private final FactObserver nopFactObserver = f -> {
    // do nothing
  };

  @BeforeEach
  void setup() {
    List<Fact> facts = List.of(
        Fact.of("{\"id\":\"" + UUID.randomUUID() + "\", \"ns\":\"users\",\"type\":\"UserCreated\"}", "{}"),
        Fact.of("{\"id\":\"" + UUID.randomUUID() + "\", \"ns\":\"no-permissions\",\"type\":\"UserCreated\"}", "{}"));
    String insertFact = "INSERT INTO fact(header,payload) VALUES (cast(? as jsonb),cast (? as jsonb))";
    jdbcTemplate.batchUpdate(
        insertFact,
        facts,
        Integer.MAX_VALUE,
        (statement, fact) -> {
          statement.setString(1, fact.jsonHeader());
          statement.setString(2, fact.jsonPayload());
        });
  }

  @Test
  public void allowedToPublish() {
    fc.publish(
        Fact.of(
            "{\"id\":\"" + UUID.randomUUID() + "\", \"ns\":\"users\",\"type\":\"UserCreated\"}",
            "{}"));
  }

  @Test
  public void failsToPublish() {
    assertThatThrownBy(
            () ->
                fc.publish(
                    Fact.of(
                        "{\"id\":\""
                            + UUID.randomUUID()
                            + "\", \"ns\":\"no-permissions\",\"type\":\"UserCreated\"}",
                        "{}")))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("PERMISSION_DENIED");
  }

  @Test
  public void allowedToCatchup() throws Exception {
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("users").type("UserCreated")).fromScratch();
    try(Subscription sub = fc.subscribe(req, nopFactObserver)) {
      sub.awaitCatchup();
    }
  }

  @Test
  public void failsToCatchup() throws Exception {
    SubscriptionRequest req = SubscriptionRequest.catchup(FactSpec.ns("no-permissions").type("UserCreated")).fromScratch();
    try(Subscription sub = fc.subscribe(req, nopFactObserver)) {
      assertThatThrownBy(
          sub::awaitCatchup)
          .isInstanceOf(StatusRuntimeException.class)
          .hasMessageContaining("PERMISSION_DENIED");
    }
  }

  @Test
  public void allowedToFollow() throws Exception {
    SubscriptionRequest req = SubscriptionRequest.follow(FactSpec.ns("users").type("UserCreated")).fromScratch();
    try(Subscription sub = fc.subscribe(req, nopFactObserver)) {
      sub.awaitCatchup();
    }
  }

  @Test
  public void failsToFollow() throws Exception {
    SubscriptionRequest req = SubscriptionRequest.follow(FactSpec.ns("no-permissions").type("UserCreated")).fromScratch();
    try(Subscription sub = fc.subscribe(req, nopFactObserver)) {
      assertThatThrownBy(
          sub::awaitCatchup)
          .isInstanceOf(StatusRuntimeException.class)
          .hasMessageContaining("PERMISSION_DENIED");
    }
  }

  @Test
  public void allowedToEnumerate() {
    assertThat(fc.enumerateNamespaces()).contains("users");
    assertThat(fc.enumerateTypes("users")).contains("UserCreated");
  }

  @Test
  public void failsToEnumerate() {
    assertThat(fc.enumerateNamespaces()).doesNotContain("no-permissions");
    assertThatThrownBy(() -> fc.enumerateTypes("no-permissions"))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("PERMISSION_DENIED");
  }

  @Test
  public void failsUnauthenticatedHandshake() {
    Channel channel = channelFactory.createChannel("factstore");
    assertThatThrownBy(() -> stubsFactory.createBlockingStub(channel).handshake(converter.empty()))
        .isInstanceOf(StatusRuntimeException.class)
        .hasMessageContaining("UNAUTHENTICATED");
  }
}
