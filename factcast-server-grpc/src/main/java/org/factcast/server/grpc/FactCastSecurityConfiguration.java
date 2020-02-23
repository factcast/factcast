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
import java.util.Optional;

import org.factcast.server.grpc.auth.FactCastAccessConfiguration;
import org.factcast.server.grpc.auth.FactCastAccount;
import org.factcast.server.grpc.auth.FactCastUser;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration;
import net.devh.boot.grpc.server.security.authentication.BasicGrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;

@SuppressWarnings("deprecation")
@Slf4j
@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, proxyTargetClass = true)
@AutoConfigureBefore(GrpcServerSecurityAutoConfiguration.class)
public class FactCastSecurityConfiguration {

    @Bean(name = "no_longer_used")
    @ConditionalOnResource(resources = "classpath:factcast-security.json")
    public Object credentialConfigurationFromClasspath() throws IOException {
        throw new IllegalArgumentException(
                "classpath:factcast-security.json was removed in this release. Please read the migration guide.");
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(FactCastAccessConfiguration.class)
    @ConditionalOnResource(resources = "classpath:factcast-access.json")
    public FactCastAccessConfiguration authenticationConfig() throws IOException {
        try (InputStream is = new ClassPathResource("/factcast-access.json").getInputStream()) {
            return FactCastAccessConfiguration.read(is);
        }
    }

    // security on

    @Bean
    @Primary
    @ConditionalOnBean(FactCastAccessConfiguration.class)
    UserDetailsService userDetailsService(FactCastAccessConfiguration cc) {
        log.info("FactCast Security is enabled.");
        return username -> {
            log.debug("*** username is " + username);
            Optional<FactCastAccount> account = cc.findAccountByName(username);
            return account
                    .map(this::toUser)
                    .orElseThrow(() -> new UsernameNotFoundException(username));
        };
    }

    private FactCastUser toUser(FactCastAccount a) {
        return new FactCastUser(a);
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
            @org.springframework.beans.factory.annotation.Value("${insecure:#{false}}") Boolean insecureIsOk) {
        if (insecureIsOk) {
            log.warn(
                    "**** FactCast Security is disabled. This is discouraged for production environments. You have been warned. ****");
            return username -> new FactCastUser(FactCastAccount.GOD);
        }

        log.error("**** FactCast Security is disabled. ****");
        log.error("* If you really want to, you can run Factcast in such a configuration ");
        log.error("* by adding a property '-Dinsecure' to your setup. However, it is");
        log.error("* highly encouraged to provide a factcast-access.json instead.");
        log.error("**** -> see https://docs.factcast.org/setup/examples/grpc-config-basicauth/");
        System.exit(1);
        // dead code
        return null;
    }

    @Bean
    @ConditionalOnMissingBean(FactCastAccessConfiguration.class)
    GrpcAuthenticationReader noOpAuthenticationReader() {
        UsernamePasswordAuthenticationToken disabled = new UsernamePasswordAuthenticationToken(
                "security_disabled", "security_disabled");
        return (call, headers) -> disabled;
    }
}
