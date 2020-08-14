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
import org.factcast.core.event.EventConverter;
import org.factcast.core.event.EventSerializer;
import org.factcast.core.snap.SnapshotCache;
import org.factcast.factus.DefaultFactus;
import org.factcast.factus.Factus;
import org.factcast.factus.applier.DefaultEventApplierFactory;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.factus.snapshot.AggregateSnapshotRepositoryImpl;
import org.factcast.factus.snapshot.ProjectionSnapshotRepositoryImpl;
import org.factcast.factus.snapshot.SnapshotSerializerSupplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.Generated;

@Configuration
@ConditionalOnClass(Factus.class)
@ConditionalOnMissingBean(Factus.class)
@Generated
public class FactusAutoConfiguration {

    @Bean
    public Factus factus(FactCast fc, SnapshotCache sr, EventSerializer deserializer,
            EventConverter eventConverter,
            SnapshotSerializerSupplier snapshotSerializerSupplier) {
        return new DefaultFactus(fc, new DefaultEventApplierFactory(deserializer), eventConverter,
                new AggregateSnapshotRepositoryImpl(sr, snapshotSerializerSupplier),
                new ProjectionSnapshotRepositoryImpl(sr, snapshotSerializerSupplier),
                snapshotSerializerSupplier);
    }

    @Bean
    public SnapshotSerializerSupplier snapshotSerializerSupplier(Set<SnapshotSerializer> ser) {
        return new SnapshotSerializerSupplier(ser);
    }

}
