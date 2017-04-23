package org.factcast.core;

import org.factcast.core.store.FactStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class FactCastConfiguration {

	@Bean
	@Primary
	public FactCast factCast(FactStore store) {
		return FactCast.from(store);
	}

}
