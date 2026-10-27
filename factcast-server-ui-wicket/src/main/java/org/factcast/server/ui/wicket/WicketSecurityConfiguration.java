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
package org.factcast.server.ui.wicket;

import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class WicketSecurityConfiguration {
  @Bean
  SecurityFilterChain wicketSecurityFilterChain(HttpSecurity http) {
    return http.authorizeHttpRequests(
            auth ->
                auth.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ERROR)
                    .permitAll()
                    .requestMatchers("/login", "/assets/**", "/wicket/resource/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .formLogin(login -> login.loginPage("/login").defaultSuccessUrl("/ui/full").permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login").invalidateHttpSession(true))
        .build();
  }
}
