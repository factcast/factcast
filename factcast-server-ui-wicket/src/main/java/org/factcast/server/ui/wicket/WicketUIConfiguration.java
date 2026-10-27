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

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import org.apache.wicket.protocol.http.WicketFilter;
import org.factcast.core.store.FactStore;
import org.factcast.server.ui.adapter.*;
import org.factcast.server.ui.config.*;
import org.factcast.server.ui.metrics.*;
import org.factcast.server.ui.plugins.JsonViewPluginService;
import org.factcast.server.ui.port.*;
import org.factcast.server.ui.security.SecurityService;
import org.springframework.boot.autoconfigure.condition.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.*;
import org.springframework.context.annotation.*;
import org.springframework.util.ClassUtils;

@Configuration(proxyBeanMethods = false)
@EnableAspectJAutoProxy
@Import({JsonViewPluginConfiguration.class, WicketSecurityConfiguration.class})
@EnableConfigurationProperties(ReportStoreConfigurationProperties.class)
public class WicketUIConfiguration {
  public WicketUIConfiguration(org.springframework.context.ApplicationContext context) {
    if (ClassUtils.isPresent(
        "org.factcast.server.ui.config.UIConfiguration", context.getClassLoader())) {
      throw new IllegalStateException(
          "Choose either factcast-server-ui or factcast-server-ui-wicket; both UI implementations are on the classpath.");
    }
  }

  @Bean
  @ConditionalOnMissingBean
  SecurityService securityService() {
    return new WicketSecurityService();
  }

  @Bean
  @ConditionalOnMissingBean
  FactRepository factRepository(FactStore store, SecurityService security) {
    return new FactRepositoryImpl(store, security);
  }

  @Bean
  @ConditionalOnMissingBean
  UiMetrics uiMetrics(Optional<MeterRegistry> registry) {
    return registry.<UiMetrics>map(MeterRegistryMetrics::new).orElseGet(UiMetrics.NOP::new);
  }

  @Bean
  @ConditionalOnBean(MeterRegistry.class)
  @ConditionalOnMissingBean
  io.micrometer.core.aop.TimedAspect timedAspect(MeterRegistry registry) {
    return new io.micrometer.core.aop.TimedAspect(registry);
  }

  @Bean
  @ConditionalOnMissingBean(ReportStore.class)
  FileSystemReportStore fileSystemReportStore(ReportStoreConfigurationProperties properties) {
    if (properties.getPath() == null || properties.getPath().isBlank()) {
      throw new IllegalArgumentException(
          "Configure factcast.ui.report.store.path or provide a ReportStore (for example S3).");
    }
    return new FileSystemReportStore(properties.getPath());
  }

  @Bean
  FactCastWicketApplication wicketApplication(
      FactRepository repository, ReportStore reports, JsonViewPluginService plugins) {
    return new FactCastWicketApplication(repository, reports, plugins);
  }

  @Bean
  FilterRegistrationBean<WicketFilter> wicketFilter(FactCastWicketApplication application) {
    var registration = new FilterRegistrationBean<>(new WicketFilter(application));
    registration.setName("factcast-wicket");
    registration.addUrlPatterns("/*");
    registration.addInitParameter("filterMappingUrlPattern", "/*");
    registration.addInitParameter("ignorePaths", "/files/,/actuator/");
    registration.setOrder(100);
    return registration;
  }

  @Bean
  @ConditionalOnBean(FileSystemReportStore.class)
  ServletRegistrationBean<ReportDownloadServlet> reportDownloadServlet(
      FileSystemReportStore store) {
    return new ServletRegistrationBean<>(new ReportDownloadServlet(store), "/files/*");
  }
}
