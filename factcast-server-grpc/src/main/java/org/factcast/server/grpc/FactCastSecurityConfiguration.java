/*
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
import java.util.*;

import org.factcast.server.grpc.auth.AccessCredential;
import org.factcast.server.grpc.auth.CredentialConfiguration;
import org.factcast.server.grpc.auth.FactCastRole;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.autoconfigure.*;
import net.devh.boot.grpc.server.security.authentication.*;

@Slf4j
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, proxyTargetClass = true)
@AutoConfigureBefore(GrpcServerSecurityAutoConfiguration.class)
public class FactCastSecurityConfiguration {

    @Bean
    @ConditionalOnMissingBean(CredentialConfiguration.class)
    @ConditionalOnResource(resources = "classpath:factcast-security.json")
    public CredentialConfiguration credentialConfigurationFromClasspath() throws IOException {
        try (InputStream is = new ClassPathResource("/factcast-security.json").getInputStream()) {
            return CredentialConfiguration.read(is);
        }
    }

    // security on

    @Bean
    @Primary
    @ConditionalOnBean(CredentialConfiguration.class)
    UserDetailsService userDetailsService(CredentialConfiguration cc) {
        log.info("FactCast Security is enabled.");
        return username -> {
            log.debug("*** username is " + username);
            cc.find(username).ifPresent(ud -> log.debug("*** found: " + ud));

            return cc.find(username)

                    .map(AccessCredential::toUser)
                    .orElseThrow(() -> new UsernameNotFoundException(username));
        };
    }

    @Bean
    @Primary
    @ConditionalOnBean(CredentialConfiguration.class)
    GrpcAuthenticationReader authenticationReader() {
        return new BasicGrpcAuthenticationReader();
    }

    @Bean
    AuthenticationManager authenticationManager(DaoAuthenticationProvider p) {
        return new ProviderManager(Collections.singletonList(p));
    }

    @SuppressWarnings("deprecation")
    @Bean
    DaoAuthenticationProvider daoAuthenticationProvider(UserDetailsService uds) {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(uds);
        provider.setPasswordEncoder(NoOpPasswordEncoder.getInstance());
        return provider;
    }

    // security off
    @Bean
    @ConditionalOnMissingBean(CredentialConfiguration.class)
    UserDetailsService godModeUserDetailsService() {
        log.warn(
                "**** FactCast Security is disabled. This is discouraged for production environments. You have been warned. ****");
        List<GrantedAuthority> fullAccess = AuthorityUtils
                .commaSeparatedStringToAuthorityList(FactCastRole.READ + ","
                        + FactCastRole.WRITE);
        String DISABLED = "security_disabled";
        return username -> new User(DISABLED, DISABLED, fullAccess);
    }

    @Bean
    @ConditionalOnMissingBean(CredentialConfiguration.class)
    GrpcAuthenticationReader noOpAuthenticationReader() {
        UsernamePasswordAuthenticationToken disabled = new UsernamePasswordAuthenticationToken(
                "security_disabled", "security_disabled");
        return (call, headers) -> disabled;
    }
}
