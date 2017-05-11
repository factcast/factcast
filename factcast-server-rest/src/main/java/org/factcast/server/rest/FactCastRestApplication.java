package org.factcast.server.rest;

import org.factcast.server.rest.resources.FactsResource;
import org.factcast.server.rest.resources.FactsTransactionsResource;
import org.factcast.server.rest.resources.RootResource;
import org.factcast.server.rest.resources.cache.CachableFilter;
import org.factcast.server.rest.resources.cache.NoCacheFilter;
import org.factcast.server.rest.resources.converter.JsonParamConverterProvider;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
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

        register(JsonParamConverterProvider.class);
    }

}
