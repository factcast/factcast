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
import lombok.Generated;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.FactCast;
import org.factcast.core.event.EventConverter;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.factus.Factus;
import org.factcast.factus.FactusImpl;
import org.factcast.factus.event.EventSerializer;
import org.factcast.factus.metrics.FactusMetrics;
import org.factcast.factus.metrics.FactusMetricsImpl;
import org.factcast.factus.projector.DefaultProjectorFactory;
import org.factcast.factus.projector.ProjectorFactory;
import org.factcast.factus.serializer.DefaultSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.AggregateSnapshotRepositoryImpl;
import org.factcast.factus.snapshot.ProjectionSnapshotRepositoryImpl;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
@AutoConfiguration
@ConditionalOnClass(Factus.class)
@Generated
@Slf4j
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
public class FactusAutoConfiguration {

  @Bean(destroyMethod = "close")
  @ConditionalOnMissingBean
  public Factus factus(
      FactCast fc,
      SnapshotCache sr,
      EventSerializer deserializer,
      EventConverter eventConverter,
      SnapshotSerializerSupplier snapshotSerializerSupplier,
      FactusMetrics factusMetrics,
      ProjectorFactory projectorFactory) {
    return new FactusImpl(
        fc,
        projectorFactory,
        eventConverter,
        new AggregateSnapshotRepositoryImpl(sr, snapshotSerializerSupplier, factusMetrics),
        new ProjectionSnapshotRepositoryImpl(sr, snapshotSerializerSupplier, factusMetrics),
        snapshotSerializerSupplier,
        factusMetrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public ProjectorFactory projectorFactory(EventSerializer ser) {
    return new DefaultProjectorFactory(ser);
  }

  @Bean
  @ConditionalOnMissingBean
  public SnapshotSerializerSupplier snapshotSerializerSupplier(SnapshotSerializer ser) {
    return new SnapshotSerializerSupplier(ser);
  }

  @Bean
  @ConditionalOnMissingBean
  @Order(Ordered.LOWEST_PRECEDENCE)
  public SnapshotSerializer snapshotSerializer() {
    return new DefaultSnapshotSerializer();
  }

  @Bean
  @ConditionalOnMissingBean
  public FactusMetrics factusMetrics(MeterRegistry meterRegistry) {
    return new FactusMetricsImpl(meterRegistry);
  }
}
