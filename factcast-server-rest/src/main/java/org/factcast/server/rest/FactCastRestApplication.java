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
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.validation.internal.ValidationExceptionMapper;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.stereotype.Component;

/**
 * jersey configuration class
 * 
 * @author joerg_adler
 *
 */
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

        register(ValidationExceptionMapper.class);

        property(ServerProperties.BV_SEND_ERROR_IN_RESPONSE, true);
    }

}
