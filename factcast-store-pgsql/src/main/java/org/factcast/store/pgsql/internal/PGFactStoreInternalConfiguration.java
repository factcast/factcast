package org.factcast.store.pgsql.internal;

import java.util.concurrent.Executors;

import org.factcast.core.store.FactStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfiguration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;

@Configuration
@EnableScheduling
@Import(SchedulingConfiguration.class)
public class PGFactStoreInternalConfiguration {
	@Bean
	@ConditionalOnMissingBean(EventBus.class)
	public EventBus eventBus() {
		return new AsyncEventBus(this.getClass().getSimpleName(), Executors.newCachedThreadPool());
	}

	@Bean
	public PGFactIdToSerMapper pgEventIdToSerMapper(JdbcTemplate tpl) {
		return new PGFactIdToSerMapper(tpl);
	}

	@Bean
	public PGListener pgSqlListener(EventBus bus, EnvironmentPGConnectionSupplier connSup) {
		return new PGListener(connSup, bus, new ConnectionTester());
	}

	@Bean
	public EnvironmentPGConnectionSupplier environmentPGConnectionSupplier() {
		return new EnvironmentPGConnectionSupplier();
	}

	@Bean
	@ConditionalOnMissingBean(TaskScheduler.class)
	public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
		return new ThreadPoolTaskScheduler();
	}

	@Bean
	public PGFactFactory pgFactFactory(ObjectMapper om) {
		return new PGFactFactory(om);
	}

	@Bean
	public FactStore factStore(JdbcTemplate tpl, PGFactFactory pff, PGSubscriptionFactory queryProvider) {
		return new PGFactStore(tpl, pff, queryProvider);
	}

	@Bean
	public PGSubscriptionFactory pgSubscriptionFactory(JdbcTemplate tpl, EventBus bus, PGFactIdToSerMapper serMapper,
			PGFactFactory factory) {
		return new PGSubscriptionFactory(tpl, bus, serMapper, factory);
	}

	@Bean
	@ConditionalOnMissingBean(ObjectMapper.class)
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

}
