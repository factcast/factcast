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

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration;
import net.devh.boot.grpc.server.security.authentication.BasicGrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import org.factcast.server.grpc.auth.FactCastAccessConfiguration;
import org.factcast.server.grpc.auth.FactCastAccount;
import org.factcast.server.grpc.auth.FactCastUser;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

@SuppressWarnings("deprecation")
@Slf4j
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, proxyTargetClass = true)
@AutoConfigureBefore(GrpcServerSecurityAutoConfiguration.class)
@EnableConfigurationProperties
public class FactCastSecurityConfiguration {

  private static final String CLASSPATH_FACTCAST_ACCESS_JSON = "/factcast-access.json";

  @Bean(name = "no_longer_used")
  @ConditionalOnResource(resources = "classpath:factcast-security.json")
  public Object credentialConfigurationFromClasspath() {
    throw new IllegalArgumentException(
        "classpath:factcast-security.json was removed in this release. Please read the migration"
            + " guide.");
  }

  @Bean
  @ConfigurationProperties(prefix = "factcast.access", ignoreUnknownFields = false)
  public FactCastSecretProperties factCastSecretProperties() {
    return new FactCastSecretProperties();
  }

  @Bean
  @ConfigurationProperties(prefix = "factcast.security", ignoreUnknownFields = false)
  public FactcastSecurityProperties factcastSecurityProperties() {
    return new FactcastSecurityProperties();
  }

  @Bean
  @Primary
  @ConditionalOnMissingBean(FactCastAccessConfiguration.class)
  @ConditionalOnResource(
      resources = "classpath:" + FactCastSecurityConfiguration.CLASSPATH_FACTCAST_ACCESS_JSON)
  public FactCastAccessConfiguration authenticationConfig(FactCastSecretProperties accessSecrets)
      throws IOException {
    ClassPathResource access = new ClassPathResource(CLASSPATH_FACTCAST_ACCESS_JSON);

    try (InputStream is = access.getInputStream()) {
      FactCastAccessConfiguration cfg = FactCastAccessConfiguration.read(is);

      // look for secrets in properties
      List<String> ids =
          cfg.accounts().stream().map(FactCastAccount::id).collect(Collectors.toList());
      for (String id : ids) {
        if (!accessSecrets.getSecrets().containsKey(id))
          throw new IllegalArgumentException("Missing secret for account: '" + id + "'");
      }

      for (String k : accessSecrets.getSecrets().keySet()) {
        if (!ids.contains(k))
          log.warn(
              "Secret found for account '"
                  + k
                  + "' but the account is not defined in FactCastAccessConfiguration");
      }

      return cfg;
    }
  }

  // security on

  @Bean
  @Primary
  @ConditionalOnBean(FactCastAccessConfiguration.class)
  UserDetailsService userDetailsService(
      FactCastAccessConfiguration cc, FactCastSecretProperties secrets) {
    log.info("FactCast Security is enabled.");
    return username -> {
      Optional<FactCastAccount> account = cc.findAccountById(username);
      return account
          .map(a -> toUser(a, secrets.getSecrets().get(a.id())))
          .orElseThrow(() -> new UsernameNotFoundException(username));
    };
  }

  private FactCastUser toUser(FactCastAccount a, String secret) {
    return new FactCastUser(a, secret);
  }

  @Bean
  @Primary
  @ConditionalOnBean(FactCastAccessConfiguration.class)
  GrpcAuthenticationReader authenticationReader() {
    return new BasicGrpcAuthenticationReader();
  }

  @Bean
  AuthenticationManager authenticationManager(DaoAuthenticationProvider p) {
    return new ProviderManager(Collections.singletonList(p));
  }

  @Bean
  DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService uds) {
    final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(uds);
    provider.setPasswordEncoder(NoOpPasswordEncoder.getInstance());
    return provider;
  }

  // security off
  @Bean
  @ConditionalOnMissingBean(FactCastAccessConfiguration.class)
  UserDetailsService godModeUserDetailsService(
      FactcastSecurityProperties factcastSecurityProperties) {

    if (!factcastSecurityProperties.isEnabled()) {
      log.warn(
          "**** FactCast Security is disabled. This is discouraged for production environments. You"
              + " have been warned. ****");
      return username -> new FactCastUser(FactCastAccount.GOD, "security_disabled");
    }

    log.error("**** FactCast Security is disabled. ****");
    log.error("* If you really want to, you can run Factcast in such a configuration ");
    log.error(
        "* by adding a property 'factcast.security.enabled=false' to your setup. However, it is");
    log.error("* highly encouraged to provide a factcast-access.json instead.");
    log.error("**** -> see https://docs.factcast.org/setup/examples/grpc-config-basicauth/");
    System.exit(1);
    // dead code
    return null;
  }

  @Bean
  @ConditionalOnMissingBean(FactCastAccessConfiguration.class)
  GrpcAuthenticationReader noOpAuthenticationReader() {
    UsernamePasswordAuthenticationToken disabled =
        new UsernamePasswordAuthenticationToken("security_disabled", "security_disabled");
    return (call, headers) -> disabled;
  }
}
