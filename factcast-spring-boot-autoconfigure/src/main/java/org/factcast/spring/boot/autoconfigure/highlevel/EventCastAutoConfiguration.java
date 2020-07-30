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

import org.factcast.core.FactCast;
import org.factcast.core.snap.SnapshotRepository;
import org.factcast.highlevel.EventCast;
import org.factcast.highlevel.applier.DefaultEventApplierFactory;
import org.factcast.highlevel.applier.DefaultEventDeserializer;
import org.factcast.highlevel.applier.EventDeserializer;
import org.factcast.highlevel.snapshot.AggregateSnapshotRepositoryImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Generated;

@Configuration
@ConditionalOnClass(EventCast.class)
@ConditionalOnMissingBean(EventCast.class)
@Generated
public class EventCastAutoConfiguration {

    @Bean
    public EventCast eventCast(FactCast fc, SnapshotRepository sr, EventDeserializer deserializer) {
        return new EventCast(fc, new DefaultEventApplierFactory(deserializer),
                new AggregateSnapshotRepositoryImpl(sr));
    }

    @Bean
    @ConditionalOnMissingBean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public EventDeserializer eventDeserializer(ObjectMapper om) {
        return new DefaultEventDeserializer(om);
    }
}
