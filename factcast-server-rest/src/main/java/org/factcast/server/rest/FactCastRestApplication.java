package org.factcast.server.rest;

import java.util.logging.Level;

import org.factcast.server.rest.resources.FactsResource;
import org.factcast.server.rest.resources.FactsTransactionsResource;
import org.factcast.server.rest.resources.RootResource;
import org.factcast.server.rest.resources.cache.CachableFilter;
import org.factcast.server.rest.resources.cache.NoCacheFilter;
import org.factcast.server.rest.resources.converter.JsonParamConverterProvider;
import org.factcast.server.rest.resources.cors.CorsFilter;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.logging.LoggingFeature;
import org.glassfish.jersey.logging.LoggingFeature.Verbosity;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.stereotype.Component;

@Component
public class FactCastRestApplication extends ResourceConfig {

    public FactCastRestApplication() {
        register(JacksonFeature.class);

        register(RootResource.class);
        register(FactsTransactionsResource.class);
        register(FactsResource.class);

        register(NoCacheFilter.class);
        register(CachableFilter.class);

        register(CorsFilter.class);
        register(JsonParamConverterProvider.class);

        SLF4JBridgeHandler.install();
        java.util.logging.Logger log = java.util.logging.Logger.getLogger("org.factcast.requests");

        LoggingFeature loggingFeature = new LoggingFeature(log, Level.FINEST, Verbosity.PAYLOAD_ANY,
                2000);

        register(loggingFeature);
    }

}
