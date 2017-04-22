package org.factcast.server.grpc.config;

import org.factcast.server.grpc.api.conv.ProtoConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FactGrpcServerConfiguration {
	// TODO move

	@Bean
	public ProtoConverter protoConverter() {
		return new ProtoConverter();
	}
}
