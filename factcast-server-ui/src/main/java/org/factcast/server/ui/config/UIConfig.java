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
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import com.vaadin.flow.theme.Theme;
import org.factcast.server.ui.views.LoginView;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@EnableWebSecurity
@Configuration
@Theme(value = "fcui")
public class UIConfig extends VaadinWebSecurity implements AppShellConfigurator {

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.authorizeHttpRequests(
        auth ->
            auth.requestMatchers(AntPathRequestMatcher.antMatcher(HttpMethod.GET, "/images/*.png"))
                .permitAll());
    super.configure(http);
    setLoginView(http, LoginView.class);
  }

  @Bean
  @Primary
  public UserDetailsService users() {
    System.out.println("USERS1");
    UserDetails user =
        User.builder()
            .username("user")
            // password = password with this hash, don't tell anybody :-)
            .password("pwd")
            .roles("USER")
            .build();
    UserDetails admin =
        User.builder().username("admin").password("pwd").roles("USER", "ADMIN").build();
    return new InMemoryUserDetailsManager(user, admin) {
      @Override
      public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // TODO Auto-generated method stub
        System.out.println("loadUserByUsername " + username);
        return super.loadUserByUsername(username);
      }
    };
  }
}
