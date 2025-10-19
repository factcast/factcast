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
package org.factcast.spring.boot.autoconfigure.factus;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactCast;
import org.factcast.factus.Factus;
import org.factcast.factus.FactusImpl;
import org.factcast.factus.event.EventConverter;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.lock.InLockedOperation;
import org.factcast.factus.lock.InLockedOperationJava1_8;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.FactusMetricsImpl;
import org.factcast.factus.projection.parameter.HandlerParameterContributors;
import org.factcast.factus.projector.DefaultProjectorFactory;
import org.factcast.factus.projector.ProjectorFactory;
import org.factcast.factus.serializer.DefaultSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.*;
import org.factcast.factus.utils.FactusDependency;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfiguration
@ConditionalOnClass(Factus.class)
@Slf4j
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
public class FactusAutoConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public Factus factus(
      FactCast fc,
      SnapshotCache sr,
      EventConverter eventConverter,
      SnapshotSerializerSelector snapshotSerializerSelector,
      FactusMetrics factusMetrics,
      ProjectorFactory projectorFactory,
      InLockedOperation inLockedOperation,
      /** not used but part of parameters to ensure the dependency graph can be inspected */
      @SuppressWarnings("unused") Set<FactusDependency> dependencies) {

    return new FactusImpl(
        fc,
        projectorFactory,
        eventConverter,
        new AggregateRepository(sr, snapshotSerializerSelector, factusMetrics),
        new SnapshotRepository(sr, snapshotSerializerSelector, factusMetrics),
        factusMetrics,
        inLockedOperation);
  }

  /**
   * Still uses a thread-local. Include factcast-factus-jdk25 to replace this with a version that is
   * safe to use with virtual threads.
   */
  @Bean
  @ConditionalOnMissingBean
  public InLockedOperation inLockedOperation() {
    if (InLockedOperationJava1_8.isVirtualThreadSupported()) {
      log.warn(
          "Support for virtual threads detected! Using factus with virtual threads is "
              + "dangerous! See README in "
              + "'factcast-factus-jdk25' on how to fix this!");
    }
    return new InLockedOperationJava1_8();
  }

  @Bean
  @ConditionalOnMissingBean
  public ProjectorFactory projectorFactory(EventSerializer ser) {
    return new DefaultProjectorFactory(ser, new HandlerParameterContributors());
  }

  @Bean
  @ConditionalOnMissingBean
  public SnapshotSerializerSelector snapshotSerializerSelector(
      ApplicationContext ctx, SnapshotSerializer defaultSnapshotSerializer) {
    return new SnapshotSerializerSelector(
        defaultSnapshotSerializer,
        new SpringSnapshotSerializerSupplier(ctx, new SnapshotSerializerSupplier.Default()));
  }

  @Bean
  @ConditionalOnMissingBean
  public SnapshotSerializer defaultSnapshotSerializer() {
    return new DefaultSnapshotSerializer();
  }

  @Bean
  @ConditionalOnMissingBean
  public FactusMetrics factusMetrics(MeterRegistry meterRegistry) {
    return new FactusMetricsImpl(meterRegistry);
  }

  @Bean
  @ConditionalOnMissingBean
  public EventConverter eventConverter(@NonNull EventSerializer ser) {
    return new EventConverter(ser);
  }
}
