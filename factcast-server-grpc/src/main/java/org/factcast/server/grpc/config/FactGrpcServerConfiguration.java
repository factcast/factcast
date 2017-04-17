package org.factcast.server.grpc.config;

import org.factcast.core.DefaultFactFactory;
import org.factcast.server.grpc.api.ProtoConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class FactGrpcServerConfiguration {
	// TODO move
	@Bean
	public DefaultFactFactory defaultFactFactory(ObjectMapper jackson) {
		return new DefaultFactFactory(jackson);
	}

	@Bean
	public ProtoConverter protoConverter(DefaultFactFactory factory) {
		return new ProtoConverter(factory);
	}
}
