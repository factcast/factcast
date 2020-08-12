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
package org.factcast.spring.boot.autoconfigure.highlevel;

import java.util.Set;

import org.factcast.core.FactCast;
import org.factcast.core.snap.SnapshotRepository;
import org.factcast.factus.DefaultFactus;
import org.factcast.factus.Factus;
import org.factcast.factus.applier.DefaultEventApplierFactory;
import org.factcast.factus.serializer.EventSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.AggregateSnapshotRepositoryImpl;
import org.factcast.factus.snapshot.ProjectionSnapshotRepositoryImpl;
import org.factcast.factus.snapshot.SnapshotFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Generated;

@Configuration
@ConditionalOnClass(Factus.class)
@ConditionalOnMissingBean(Factus.class)
@Generated
public class EventCastAutoConfiguration {

    @Bean
    public Factus eventCast(FactCast fc, SnapshotRepository sr, EventSerializer deserializer,
            SnapshotFactory snapshotFactory) {
        return new DefaultFactus(fc, new DefaultEventApplierFactory(deserializer), deserializer,
                new AggregateSnapshotRepositoryImpl(sr), new ProjectionSnapshotRepositoryImpl(sr),
                snapshotFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public EventSerializer eventSerializer(ObjectMapper om) {
        return new EventSerializer.Default(om);
    }

    @Bean
    public SnapshotFactory snapshotFactory(Set<SnapshotSerializer> ser) {
        return new SnapshotFactory(ser);
    }

}
