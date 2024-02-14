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

import java.time.Duration;
import lombok.Data;
import lombok.experimental.Accessors;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "factcast.grpc.client")
@Data
@Accessors(fluent = false)
public class FactCastGrpcClientProperties {

  private int catchupBatchsize = 50;

  private boolean enableFastForward = true;

  private String id = null;

  private String user = null;
  private String password = null;

  /**
   * Ignores and skips duplicate exceptions during publishing (does not include conditional
   * publishing when using locks).
   *
   * <p>This might be convenient in cases where you published to a factcast server and get a
   * connection error back (you cannot possibly know if the publish on the server succeeded or not).
   *
   * <p>if you have resilience enabled, the publish would be retried and might (if the first was
   * successful) result in a DuplicateFactException. Setting this to true will make factcast just
   * ignore the exception and go on.
   *
   * <p>Note, that there is no guarantee that the duplicate id of a fact, that will cause the
   * exception on the server, identifies the same payload. Always make sure to create good random
   * UUIDs for your facts.
   *
   * <p>Also, there might be a <b>performance problem</b> resulting from this: If you publish a
   * batch of facts and a DuplicateFactException is recieved, factcast will fall back to publishing
   * every single Fact from the batch one-by-one in order to make sure, that after your call, all
   * Facts that are not duplicates will be published.
   *
   * @since 0.5.7
   */
  private boolean ignoreDuplicateFacts = false;

  @NestedConfigurationProperty
  private ResilienceConfiguration resilience = new ResilienceConfiguration();

  @Data
  @Accessors(fluent = false)
  public static class ResilienceConfiguration {
    private boolean enabled = true;

    private Duration window = Duration.ofSeconds(30);

    private int attempts = 10;

    private Duration interval = Duration.ofMillis(100);
  }
}
