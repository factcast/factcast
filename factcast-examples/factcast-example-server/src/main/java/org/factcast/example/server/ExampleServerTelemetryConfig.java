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

import org.factcast.example.server.telemetry.CatchupTelemetryListener;
import org.factcast.store.internal.telemetry.PgStoreTelemetry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExampleServerTelemetryConfig {

  @Bean
  public CatchupTelemetryListener catchupTelemetryListener(PgStoreTelemetry telemetry) {
    return new CatchupTelemetryListener(telemetry);
  }
}
