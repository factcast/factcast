package org.factcast.client.grpc;

import org.factcast.core.FactCastConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

@Import(FactCastConfiguration.class)
@Configuration
public class GrpcFactStoreConfiguration {

	@Bean
	public GrpcFactStore factStore(AddressChannelFactory af) {
		return new GrpcFactStore(af);
	}
}
