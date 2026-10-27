/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.spring.boot.autoconfigure.server.ui;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.factcast.core.store.FactStore;
import org.factcast.server.ui.port.*;
import org.factcast.server.ui.wicket.FactCastWicketApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

class FactCastServerWicketUIAutoConfigurationTest {
  private final WebApplicationContextRunner runner =
      new WebApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  FactCastServerWicketUIAutoConfiguration.class,
                  SecurityAutoConfiguration.class,
                  ServletWebSecurityAutoConfiguration.class))
          .withBean(FactStore.class, () -> mock(FactStore.class))
          .withBean(FactRepository.class, () -> mock(FactRepository.class))
          .withBean(ReportStore.class, () -> mock(ReportStore.class))
          .withBean(UserDetailsService.class, () -> mock(UserDetailsService.class));

  @Test
  void startsWithoutVaadinAndUsesSuppliedServices() {
    runner
        .withClassLoader(
            new FilteredClassLoader(
                "org.factcast.server.ui.config.UIConfiguration",
                "org.factcast.server.ui.config.SecurityConfiguration",
                "com.vaadin"))
        .run(
            context -> {
              assertThat(context)
                  .hasNotFailed()
                  .hasSingleBean(FactCastWicketApplication.class)
                  .hasSingleBean(SecurityFilterChain.class)
                  .hasSingleBean(FactRepository.class)
                  .hasSingleBean(ReportStore.class);
            });
  }

  @Test
  void backsOffWithoutWicket() {
    runner
        .withClassLoader(new FilteredClassLoader("org.factcast.server.ui.wicket"))
        .run(
            context ->
                assertThat(context)
                    .hasNotFailed()
                    .doesNotHaveBean(FactCastWicketApplication.class));
  }

  @Test
  void explainsConflictingUiDependencies() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining(
                    "Choose either factcast-server-ui or factcast-server-ui-wicket"));
  }
}
