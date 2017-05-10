package org.factcast.server.rest;

import org.factcast.server.rest.resources.FactsResource;
import org.factcast.server.rest.resources.FactsTransactionsResource;
import org.factcast.server.rest.resources.RootResource;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkMetaFactory;
import com.mercateo.common.rest.schemagen.plugin.FieldCheckerForSchema;
import com.mercateo.common.rest.schemagen.plugin.MethodCheckerForLink;
import com.mercateo.rest.schemagen.spring.JerseyHateoasConfiguration;

@Configuration
@Import(JerseyHateoasConfiguration.class)
public class FactCastRestConfig {

    @Bean
    @ConditionalOnMissingBean(ResourceConfig.class)
    public FactCastRestApplication factCastRestApplication() {
        return new FactCastRestApplication();
    }

    @Bean
    FieldCheckerForSchema fieldCheckerForSchema() {
        return (field, callContext) -> true;
    }

    @Bean
    MethodCheckerForLink methodCheckerForLink() {
        return (scope) -> true;
    }

    @Bean
    LinkFactory<RootResource> rootResourceLinkFactory(LinkMetaFactory linkMetaFactory) {
        return linkMetaFactory.createFactoryFor(RootResource.class);
    }

    @Bean
    LinkFactory<FactsTransactionsResource> transactionsResourceLinkFactory(
            LinkMetaFactory linkMetaFactory) {
        return linkMetaFactory.createFactoryFor(FactsTransactionsResource.class);
    }

    @Bean
    LinkFactory<FactsResource> factsResourceLinkFactory(LinkMetaFactory linkMetaFactory) {
        return linkMetaFactory.createFactoryFor(FactsResource.class);
    }
}
