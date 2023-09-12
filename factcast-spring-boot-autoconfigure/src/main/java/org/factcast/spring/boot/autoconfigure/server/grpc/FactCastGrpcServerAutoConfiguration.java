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
package org.factcast.spring.boot.autoconfigure.server.grpc;

import lombok.Generated;
import net.devh.boot.grpc.server.autoconfigure.*;
import net.devh.boot.grpc.server.security.authentication.GrpcAuthenticationReader;
import net.devh.boot.grpc.server.security.check.GrpcSecurityMetadataSource;
import net.devh.boot.grpc.server.security.interceptors.AuthenticatingServerInterceptor;
import net.devh.boot.grpc.server.security.interceptors.AuthorizationCheckingServerInterceptor;
import net.devh.boot.grpc.server.security.interceptors.DefaultAuthenticatingServerInterceptor;
import net.devh.boot.grpc.server.security.interceptors.ExceptionTranslatingServerInterceptor;
import org.factcast.server.grpc.FactCastGrpcServerConfiguration;
import org.factcast.server.grpc.FactCastSecurityConfiguration;
import org.factcast.server.grpc.FactStoreGrpcService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.AccessDecisionManager;
import org.springframework.security.authentication.AuthenticationManager;

@Generated
@AutoConfiguration
@Import({FactCastGrpcServerConfiguration.class, FactCastSecurityConfiguration.class})
// spring-grpc compat until release
@ImportAutoConfiguration({
  GrpcAdviceAutoConfiguration.class,
  GrpcHealthServiceAutoConfiguration.class,
  GrpcMetadataConsulConfiguration.class,
  GrpcMetadataEurekaConfiguration.class,
  GrpcMetadataNacosConfiguration.class,
  GrpcMetadataZookeeperConfiguration.class,
  GrpcReflectionServiceAutoConfiguration.class,
  GrpcServerAutoConfiguration.class,
  GrpcServerFactoryAutoConfiguration.class,
  GrpcServerMetricAutoConfiguration.class,
  GrpcServerTraceAutoConfiguration.class
})
// spring-grpc compat until release
@ConditionalOnClass(FactStoreGrpcService.class)
@ConditionalOnMissingBean(FactStoreGrpcService.class)
@AutoConfigureBefore(GrpcServerAutoConfiguration.class)
public class FactCastGrpcServerAutoConfiguration {
  // spring-grpc compat until release
  @Bean
  @ConditionalOnMissingBean
  public ExceptionTranslatingServerInterceptor exceptionTranslatingServerInterceptor() {
    return new ExceptionTranslatingServerInterceptor();
  }

  /**
   * The security interceptor that handles the authentication of requests.
   *
   * @param authenticationManager The authentication manager used to verify the credentials.
   * @param authenticationReader The authentication reader used to extract the credentials from the
   *     call.
   * @return The authenticatingServerInterceptor bean.
   */
  @Bean
  @ConditionalOnMissingBean(AuthenticatingServerInterceptor.class)
  public DefaultAuthenticatingServerInterceptor authenticatingServerInterceptor(
      final AuthenticationManager authenticationManager,
      final GrpcAuthenticationReader authenticationReader) {
    return new DefaultAuthenticatingServerInterceptor(authenticationManager, authenticationReader);
  }

  /**
   * The security interceptor that handles the authorization of requests.
   *
   * @param accessDecisionManager The access decision manager used to check the requesting user.
   * @param securityMetadataSource The source for the security metadata (access constraints).
   * @return The authorizationCheckingServerInterceptor bean.
   */
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean({AccessDecisionManager.class, GrpcSecurityMetadataSource.class})
  @SuppressWarnings("deprecation")
  public AuthorizationCheckingServerInterceptor authorizationCheckingServerInterceptor(
      final AccessDecisionManager accessDecisionManager,
      final GrpcSecurityMetadataSource securityMetadataSource) {
    return new AuthorizationCheckingServerInterceptor(
        accessDecisionManager, securityMetadataSource);
  }
  // spring-grpc compat until release
}
