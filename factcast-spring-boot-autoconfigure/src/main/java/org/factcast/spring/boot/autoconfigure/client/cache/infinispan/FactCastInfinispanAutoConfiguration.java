/**
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
package org.factcast.spring.boot.autoconfigure.client.cache.infinispan;

import java.util.concurrent.TimeUnit;

import org.factcast.client.cache.CachingFactLookup;
import org.factcast.core.DefaultFact;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.configuration.cache.SingleFileStoreConfigurationBuilder;
import org.infinispan.eviction.EvictionType;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.spring.provider.SpringEmbeddedCacheManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates inifinispan cacheManager and configures cache to use for factCast.
 * <p>
 * Note that you can also use infinispan directly via its spring
 * integration/jcache. This package just exists for having reasonable defaults
 * without the risk of conflicting with any configuration files.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Configuration
@lombok.Generated
@EnableCaching
@Slf4j
@ConditionalOnClass(org.infinispan.Version.class)
public class FactCastInfinispanAutoConfiguration {

    @Bean
    public SpringEmbeddedCacheManager cacheManager(
            @Value("${factcast.cache.infinispan.path:#{systemProperties['java.io.tmpdir']+ '/factcast'}}") String folder) {
        final SpringEmbeddedCacheManager sm = new SpringEmbeddedCacheManager(
                new DefaultCacheManager());

        log.info("Configure to persist cached objects to '{}'", folder);
        ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();
        final PersistenceConfigurationBuilder persistence = configurationBuilder.persistence()
                .passivation(false);
        final SingleFileStoreConfigurationBuilder filestore = persistence.addSingleFileStore()
                .location(folder);
        filestore.async();
        filestore.eviction().type(EvictionType.COUNT).size(10_000_000_000L);
        filestore.expiration().maxIdle(365 * 30, TimeUnit.DAYS);
        filestore.indexing().addIndexedEntity(DefaultFact.class);
        filestore.fetchPersistentState(true);
        filestore.preload(false);
        org.infinispan.configuration.cache.Configuration c = filestore.build();
        final String cacheName = CachingFactLookup.CACHE_NAME;
        log.info("Configuring cache " + cacheName);
        sm.getNativeCacheManager().defineConfiguration(cacheName, c);
        log.info("Infinispan initialization done.");
        return sm;
    }

}
