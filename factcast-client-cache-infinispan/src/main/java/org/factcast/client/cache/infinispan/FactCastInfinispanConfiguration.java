package org.factcast.client.cache.infinispan;

import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.factcast.client.cache.CachingFactCast;
import org.factcast.client.cache.CachingFactCastConfiguration;
import org.factcast.client.cache.CachingFactLookup;
import org.factcast.core.DefaultFact;
import org.factcast.core.FactCast;
import org.factcast.core.store.FactStore;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.configuration.cache.PersistenceConfigurationBuilder;
import org.infinispan.configuration.cache.SingleFileStoreConfigurationBuilder;
import org.infinispan.eviction.EvictionType;
import org.infinispan.manager.DefaultCacheManager;
import org.infinispan.spring.provider.SpringEmbeddedCacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Import({ CachingFactCastConfiguration.class })
@EnableCaching
@Slf4j
public class FactCastInfinispanConfiguration {
	@Bean
	public CachingFactCast cachingFactCast(FactStore store, CachingFactLookup cl) {
		return new CachingFactCast(FactCast.from(store), cl);
	}

	@Bean
	InfinispanInitialization infinispanInitialization(SpringEmbeddedCacheManager cm) {
		return new InfinispanInitialization(cm);
	}

	@Bean
	public SpringEmbeddedCacheManager cacheManager() {
		return new SpringEmbeddedCacheManager(new DefaultCacheManager());
	}

	@RequiredArgsConstructor
	static class InfinispanInitialization {

		final SpringEmbeddedCacheManager cm;

		@PostConstruct
		public void init() {
			final String folder = System.getProperty("java.io.tmpdir") + "/factcast";
			log.info("initializing infinispan to persistently cache to " + folder);
			ConfigurationBuilder configurationBuilder = new ConfigurationBuilder();

			final PersistenceConfigurationBuilder persistence = configurationBuilder.persistence().passivation(false);
			final SingleFileStoreConfigurationBuilder filestore = persistence.addSingleFileStore().location(folder);
			filestore.async();
			filestore.eviction().type(EvictionType.COUNT).size(10_000_000_000L);
			filestore.expiration().maxIdle(365 * 30, TimeUnit.DAYS);
			filestore.indexing().addIndexedEntity(DefaultFact.class);
			filestore.fetchPersistentState(true);
			filestore.preload(false);

			org.infinispan.configuration.cache.Configuration c = filestore.build();

			final String cacheName = CachingFactLookup.CACHE_NAME;
			log.info("Configuring cache " + cacheName);
			cm.getNativeCacheManager().defineConfiguration(cacheName, c);
			log.info("Infinispan initialization done.");
		}
	}
}
