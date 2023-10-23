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
import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.factcast.core.store.LocalFactStore;
import org.factcast.server.ui.adapter.FactRepositoryImpl;
import org.factcast.server.ui.metrics.MeterRegistryMetrics;
import org.factcast.server.ui.metrics.UiMetrics;
import org.factcast.server.ui.port.FactRepository;
import org.factcast.server.ui.security.DefaultSecurityService;
import org.factcast.server.ui.security.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.*;

@Configuration
@Theme(value = "fcui")
@EnableVaadin("org.factcast.server.ui")
@RequiredArgsConstructor
@Import(JsonViewPluginConfig.class)
public class UIConfig implements AppShellConfigurator {
  @Bean
  public FactRepository factRepository(LocalFactStore fs, SecurityService securityService) {
    return new FactRepositoryImpl(fs, securityService);
  }

  @Bean
  @ConditionalOnMissingBean
  public SecurityService securityService(AuthenticationContext authenticationContext) {
    return new DefaultSecurityService(authenticationContext);
  }

  @Bean
  @ConditionalOnMissingBean
  public UiMetrics uiMetrics(@Autowired(required = false) MeterRegistry meterRegistry) {
    if (meterRegistry == null) {
      return new UiMetrics.NOP();
    }

    return new MeterRegistryMetrics(meterRegistry);
  }

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  public TimedAspect timedAspect(MeterRegistry registry) {
    return new TimedAspect(registry);
  }
}
