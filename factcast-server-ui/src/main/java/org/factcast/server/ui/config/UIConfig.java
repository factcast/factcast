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
import com.vaadin.flow.spring.security.AuthenticationContext;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import com.vaadin.flow.theme.Theme;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.server.ui.views.LoginView;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@EnableWebSecurity
@Configuration
@Theme(value = "fcui")
@ComponentScan(basePackages = "org.factcast.server.ui")
@RequiredArgsConstructor
public class UIConfig extends VaadinWebSecurity implements AppShellConfigurator, InitializingBean {

  static {
    // TODO remove
    System.err.println("UIConfig loaded");
  }

  {
    // TODO remove
    System.err.println("UIConfig instanciated");
  }

  final FactCast fc;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/images/*.png"))
                .permitAll()
                .requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/ace-builds/**"))
                .permitAll());
    super.configure(http);
    setLoginView(http, LoginView.class);
  }

  @Bean
  @Primary
  public SecurityService vaadinSecurityService(AuthenticationContext ctx) {
    // TODO remove
    System.err.println("UIConfig.vaadinSecurityService executed");
    return new SecurityService(ctx);
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    // TODO
    fc.publish(
        Fact.builder()
            .ns("users")
            .type("UserCreated")
            .version(1)
            .aggId(UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec085"))
            .id(UUID.fromString("da716582-1fe2-4576-917b-124d3a4ec084"))
            .build(
                "{\"firstName\":\"Peter\", \"lastName\":\"Lustig\", \"foo\":[{\"bar\": \"baz\"}]}"));
  }
}
