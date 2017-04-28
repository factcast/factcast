package org.factcast.client.grpc;

import org.factcast.core.FactCastConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import net.devh.springboot.autoconfigure.grpc.client.AddressChannelFactory;

/**
 * Provides a GrpcFactStore as a FactStore impl
 * 
 * @author uwe.schaefer@mercateo.com
 *
 */
@Import(FactCastConfiguration.class)
@Configuration
public class GrpcFactStoreConfiguration {

    @Bean
    public GrpcFactStore grpcFactStore(AddressChannelFactory af) {
        return new GrpcFactStore(af);
    }
}
