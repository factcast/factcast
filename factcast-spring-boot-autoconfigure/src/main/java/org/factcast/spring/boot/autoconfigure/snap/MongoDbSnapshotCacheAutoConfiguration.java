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
package org.factcast.spring.boot.autoconfigure.snap;

import com.mongodb.client.MongoClient;
import lombok.NonNull;
import org.factcast.core.snap.mongo.MongoDbSnapshotCache;
import org.factcast.core.snap.mongo.MongoDbSnapshotProperties;
import org.factcast.factus.snapshot.SnapshotCache;
import org.factcast.factus.snapshot.SnapshotSerializerSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnClass({MongoDbSnapshotCache.class, MongoClient.class})
@ConditionalOnMissingBean(SnapshotCache.class)
@Import({MongoDbSnapshotProperties.class})
@AutoConfigureBefore(NoSnapshotCacheAutoConfiguration.class)
public class MongoDbSnapshotCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SnapshotCache snapshotCache(
            @NonNull MongoClient client, @Value("${spring.data.mongodb.database:factcast}") String databaseName,
            @NonNull MongoDbSnapshotProperties props,
            @NonNull SnapshotSerializerSelector selector) {
        return new MongoDbSnapshotCache(client, databaseName, selector, props);
    }
}
