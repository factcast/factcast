/*
 * Copyright © 2017-2024 factcast.org
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
package org.factcast.example.server;

import java.util.concurrent.TimeUnit;
import org.factcast.example.server.telemetry.MyTelemetryListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.stereotype.Component;

@Component
public class ActuatorContributor implements InfoContributor {

  @Autowired MyTelemetryListener listener;

  public void contribute(Info.Builder builder) {
    builder.withDetail(
        "followingSubscriptionsInfo",
        listener.getFollowingSubscriptionsInfo().stream()
            .map(i -> i.getMessage() + " following for " + i.getTime(TimeUnit.SECONDS) + "s")
            .toList());
  }
}
