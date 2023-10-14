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

import java.util.Collections;
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration;
import net.devh.boot.grpc.server.security.authentication.BasicGrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import org.factcast.server.security.auth.FactCastSecurityProperties;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

@SuppressWarnings("deprecation")
@Slf4j
@Generated
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, proxyTargetClass = true)
@AutoConfigureBefore(GrpcServerSecurityAutoConfiguration.class)
public class FactCastSecurityConfiguration {

  @Bean
  GrpcAuthenticationReader authenticationReader(FactCastSecurityProperties p) {
    if (p.isEnabled()) {
      return new BasicGrpcAuthenticationReader();
    }

    UsernamePasswordAuthenticationToken disabled =
        new UsernamePasswordAuthenticationToken("security_disabled", "security_disabled");
    return (call, headers) -> disabled;
  }

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
}
