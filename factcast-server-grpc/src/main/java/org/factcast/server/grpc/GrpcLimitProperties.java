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
package org.factcast.server.grpc;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Configuration
@ConfigurationProperties(prefix = "factcast.grpc.bandwidth")
@Data
@Validated
public class GrpcLimitProperties {

  private int initialNumberOfFollowRequestsAllowedPerClient = 50;

  private int numberOfFollowRequestsAllowedPerClientPerMinute = 5;

  private int initialNumberOfCatchupRequestsAllowedPerClient = 36000;

  private int numberOfCatchupRequestsAllowedPerClientPerMinute = 6000;

  private boolean disabled = false;

  // Spring needs classic setters
  public void setInitialNumberOfFollowRequestsAllowedPerClient(
      int initialNumberOfFollowRequestsAllowedPerClient) {
    this.initialNumberOfFollowRequestsAllowedPerClient =
        initialNumberOfFollowRequestsAllowedPerClient;
  }

  public void setNumberOfFollowRequestsAllowedPerClientPerMinute(
      int numberOfFollowRequestsAllowedPerClientPerMinute) {
    this.numberOfFollowRequestsAllowedPerClientPerMinute =
        numberOfFollowRequestsAllowedPerClientPerMinute;
  }

  public void setInitialNumberOfCatchupRequestsAllowedPerClient(
      int initialNumberOfCatchupRequestsAllowedPerClient) {
    this.initialNumberOfCatchupRequestsAllowedPerClient =
        initialNumberOfCatchupRequestsAllowedPerClient;
  }

  public void setNumberOfCatchupRequestsAllowedPerClientPerMinute(
      int numberOfCatchupRequestsAllowedPerClientPerMinute) {
    this.numberOfCatchupRequestsAllowedPerClientPerMinute =
        numberOfCatchupRequestsAllowedPerClientPerMinute;
  }

  public void setDisabled(boolean disabled) {
    this.disabled = disabled;
  }
}
