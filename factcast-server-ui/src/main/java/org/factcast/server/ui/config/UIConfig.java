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
package org.factcast.server.ui.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.spring.annotation.EnableVaadin;
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.theme.Theme;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.observer.FastForwardTarget;
import org.factcast.server.ui.adapter.FactRepositoryImpl;
import org.factcast.server.ui.port.FactRepository;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
@Theme(value = "fcui")
@EnableVaadin("org.factcast.server.ui")
@RequiredArgsConstructor
public class UIConfig implements AppShellConfigurator, InitializingBean {

  final FactCast fc;

  @Bean
  public FactRepository factRepository(
      FactStore fs, SecurityService securityService, FastForwardTarget fastForwardTarget) {
    return new FactRepositoryImpl(fs, securityService, fastForwardTarget);
  }

  @Bean
  @ConditionalOnMissingBean
  public SecurityService securityService(AuthenticationContext authenticationContext) {
    return new SecurityService(authenticationContext);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    // TODO
    fc.publish(
        List.of(
            Fact.builder()
                .ns("users")
                .type("UserCreated")
                .version(1)
                .aggId(UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec085"))
                .id(UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec084"))
                .build(
                    "{\"firstName\":\"Peter\", \"lastName\":\"Lustig\", \"foo\":[{\"bar\": \"baz\"}]}"),
            Fact.builder()
                .ns("users")
                .type("UserCreated")
                .version(1)
                .aggId(UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec087"))
                .id(UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec086"))
                .build(
                    "{\"firstName\":\"Werner\", \"lastName\":\"Ernst\", \"ping\":[{\"pang\": \"pong\"}]}")));
  }
}
