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
package org.factcast.server.security;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.factcast.server.security.auth.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Slf4j
@EnableConfigurationProperties
@Configuration
public class CommonSecurityConfig {
  private static final String FACTCAST_ACCESS_JSON = "/factcast-access.json";

  @Bean
  @ConditionalOnMissingBean
  @ConfigurationProperties(prefix = "factcast.access", ignoreUnknownFields = false)
  public FactCastSecretProperties factCastSecretProperties() {
    return new FactCastSecretProperties();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConfigurationProperties(prefix = "factcast.security", ignoreUnknownFields = false)
  public FactCastSecurityProperties factcastSecurityProperties() {
    return new FactCastSecurityProperties();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnResource(resources = "classpath:" + FACTCAST_ACCESS_JSON)
  public FactCastAccessConfiguration authenticationConfig(FactCastSecretProperties accessSecrets)
      throws IOException {
    ClassPathResource access = new ClassPathResource(FACTCAST_ACCESS_JSON);

    return parseAccessConfiguration(accessSecrets, access);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnResource(resources = "file:./config/" + FACTCAST_ACCESS_JSON)
  public FactCastAccessConfiguration authenticationConfigViaConfig(
      FactCastSecretProperties accessSecrets) throws IOException {
    ClassPathResource access = new ClassPathResource("file:./config/" + FACTCAST_ACCESS_JSON);

    return parseAccessConfiguration(accessSecrets, access);
  }

  @Bean
  @ConditionalOnMissingBean
  public PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  // security on

  @Bean
  @ConditionalOnBean(FactCastAccessConfiguration.class)
  @ConditionalOnMissingBean(UserDetailsService.class)
  UserDetailsService userDetailsService(
      FactCastAccessConfiguration cc,
      FactCastSecretProperties secrets,
      PasswordEncoder passwordEncoder) {
    log.info("FactCast Security is enabled.");
    return username -> {
      Optional<FactCastAccount> account = cc.findAccountById(username);
      return account
          .flatMap(
              a ->
                  Optional.of(secrets.getSecrets().get(a.id()))
                      .map(rawPassword -> toUser(a, passwordEncoder.encode(rawPassword))))
          .orElseThrow(() -> new UsernameNotFoundException(username));
    };
  }

  private FactCastUser toUser(FactCastAccount a, String secret) {
    return new FactCastUser(a, secret);
  }

  // security off
  @Bean
  @ConditionalOnMissingBean({FactCastAccessConfiguration.class, UserDetailsService.class})
  UserDetailsService godModeUserDetailsService(
      FactCastSecurityProperties factcastSecurityProperties, PasswordEncoder passwordEncoder) {

    if (!factcastSecurityProperties.isEnabled()) {
      log.warn(
          "**** FactCast Security is disabled. This is discouraged for production environments. You"
              + " have been warned. ****");
      return username ->
          new FactCastUser(FactCastAccount.GOD, passwordEncoder.encode("security_disabled"));
    }

    log.error("**** FactCast Security is disabled. ****");
    log.error("* If you really want to, you can run Factcast in such a configuration ");
    log.error(
        "* by adding a property 'factcast.security.enabled=false' to your setup. However, it is");
    log.error("* highly encouraged to provide a factcast-access.json instead.");
    log.error("**** -> see https://docs.factcast.org/setup/grpc-client/grpc-config-basicauth/");
    System.exit(1);
    // dead code
    return null;
  }

  private static FactCastAccessConfiguration parseAccessConfiguration(
      FactCastSecretProperties accessSecrets, ClassPathResource access) throws IOException {
    try (InputStream is = access.getInputStream()) {
      FactCastAccessConfiguration cfg = FactCastAccessConfiguration.read(is);

      if (accessSecrets.getSecrets().isEmpty()) {
        log.info(
            "'factcast.access' does not contain any secrets. We assume you are using a different authentication mechanism by providing a custom UserDetailsService bean.");
        return cfg;
      }

      // look for secrets in properties
      List<String> ids = cfg.accounts().stream().map(FactCastAccount::id).toList();
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
}
