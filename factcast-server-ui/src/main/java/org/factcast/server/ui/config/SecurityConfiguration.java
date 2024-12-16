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

import com.vaadin.flow.spring.security.VaadinWebSecurity;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.ui.views.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Slf4j
@Configuration
@EnableWebSecurity
public class SecurityConfiguration extends VaadinWebSecurity {
  @Override
  protected void configure(HttpSecurity http) throws Exception {
    super.configure(http);
    setLoginView(http, LoginView.class);
  }

  // Then open anything for the public API for the application
  @Order(20)
  @Bean
  SecurityFilterChain configurePublicApi(HttpSecurity http) throws Exception {
    http.securityMatcher(AntPathRequestMatcher.antMatcher("/api/v1/**"))
        .authorizeHttpRequests(authz -> authz.anyRequest().permitAll());
    return http.build();
  }
}
