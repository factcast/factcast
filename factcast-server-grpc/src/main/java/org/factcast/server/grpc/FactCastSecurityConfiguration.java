/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.server.grpc;

import static org.springframework.security.config.Customizer.withDefaults;

import java.util.Collections;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.grpc.server.security.AuthenticationProcessInterceptor;
import org.springframework.grpc.server.security.GrpcSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("deprecation")
@Slf4j
@Generated
@Configuration
@EnableMethodSecurity(securedEnabled = true, proxyTargetClass = true)
public class FactCastSecurityConfiguration {

  //  @Bean
  //  GrpcAuthenticationReader authenticationReader(FactCastSecurityProperties p) {
  //    if (p.isEnabled()) {
  //      return new BasicGrpcAuthenticationReader();
  //    }
  //
  //    UsernamePasswordAuthenticationToken disabled =
  //        new UsernamePasswordAuthenticationToken("security_disabled", "security_disabled");
  //    return (call, headers) -> disabled;
  //  }

  @Bean
  AuthenticationProvider authenticationProvider(
      UserDetailsService uds, PasswordEncoder passwordEncoder) {
    final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(uds);
    provider.setPasswordEncoder(passwordEncoder);
    return provider;
  }

  @Bean
  AuthenticationManager authenticationManager(AuthenticationProvider p) {
    return new ProviderManager(Collections.singletonList(p));
  }

  //  @Bean
  //  public SecurityFilterChain securityFilterChain(HttpSecurity http, FactCastSecurityProperties
  // p) throws Exception {
  //    if (p.isEnabled()) {
  //      return http.httpBasic(Customizer.withDefaults())
  //          .authorizeHttpRequests((requests) -> requests.anyRequest().authenticated())
  //          .build();
  //    }
  //    return http
  //        .authorizeHttpRequests((requests) -> requests.anyRequest().permitAll())
  //        .build();
  //  }

  @Bean
  @GlobalServerInterceptor
  AuthenticationProcessInterceptor jwtSecurityFilterChain(GrpcSecurity grpc) throws Exception {
    return grpc.authorizeRequests(requests -> requests.allRequests().authenticated())
        .httpBasic(withDefaults())
        // .preauth(withDefaults())
        .build();
  }
}
