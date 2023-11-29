/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.server.ui.example;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EventInitializer implements InitializingBean {
  public static final UUID USER1_AGG_ID = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec085");
  public static final UUID USER1_EVENT_ID = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec084");
  public static final UUID USER2_AGG_ID = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec087");
  public static final UUID USER2_EVENT_ID = UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec086");

  private final FactStore fs;

  @Override
  public void afterPropertiesSet() throws Exception {
    fs.publish(
        List.of(
            Fact.builder()
                .ns("users")
                .type("UserCreated")
                .version(1)
                .aggId(USER1_AGG_ID)
                .id(USER1_EVENT_ID)
                .meta("hugo", "bar")
                .build(
                    "{\"firstName\":\"Peter\", \"lastName\":\"Lustig\", \"foo\":[{\"bar\":\"baz\"}]}"),
            Fact.builder()
                .ns("users")
                .type("UserCreated")
                .version(1)
                .aggId(USER2_AGG_ID)
                .id(USER2_EVENT_ID)
                .build(
                    "{\"firstName\":\"Werner\", \"lastName\":\"Ernst\", \"ping\":[{\"pang\":\"pong\"}]}")));
  }
}
