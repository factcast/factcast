package org.factcast.server.rest.documentation.util;

import java.net.URI;
import java.net.URISyntaxException;

import org.factcast.core.store.FactStore;
import org.factcast.server.rest.FactCastRestConfiguration;
import org.factcast.server.rest.resources.FactsResource;
import org.factcast.server.rest.resources.FactsTransactionsResource;
import org.factcast.server.rest.resources.RootResource;
import org.factcast.store.inmem.InMemFactStore;
import org.springframework.beans.factory.config.CustomScopeConfigurer;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import com.mercateo.common.rest.schemagen.JsonHyperSchemaCreator;
import com.mercateo.common.rest.schemagen.JsonSchemaGenerator;
import com.mercateo.common.rest.schemagen.RestJsonSchemaGenerator;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkFactoryContext;
import com.mercateo.common.rest.schemagen.link.LinkFactoryContextDefault;
import com.mercateo.common.rest.schemagen.link.LinkMetaFactory;
import com.mercateo.common.rest.schemagen.plugin.FieldCheckerForSchema;
import com.mercateo.common.rest.schemagen.plugin.MethodCheckerForLink;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ListResponseBuilderCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchemaCreator;
import com.mercateo.common.rest.schemagen.types.PaginatedResponseBuilderCreator;

@EnableAutoConfiguration(exclude = FactCastRestConfiguration.class)
@SuppressWarnings("deprecation")
@ComponentScan(basePackageClasses = { RootResource.class, SetupRunner.class })
public class SpringConfig {

    private InMemFactStore inMemFactStore = new InMemFactStore();

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

    @Bean
    FactStore getFactStore() {
        return inMemFactStore;
    }

    @Bean
    CustomScopeConfigurer getCustomScopeConfigurer() {
        CustomScopeConfigurer cf = new CustomScopeConfigurer();
        cf.addScope("request", new org.springframework.web.context.request.RequestScope());
        return cf;
    }

    @Bean
    JsonSchemaGenerator jsonSchemaGenerator() {
        return new RestJsonSchemaGenerator();
    }

    @Bean
    LinkMetaFactory linkMetaFactory(JsonSchemaGenerator jsonSchemaGenerator,
            LinkFactoryContext linkFactoryContext) throws URISyntaxException {
        return LinkMetaFactory.create(jsonSchemaGenerator, linkFactoryContext);
    }

    @Bean
    JsonHyperSchemaCreator jsonHyperSchemaCreator() {
        return new JsonHyperSchemaCreator();
    }

    @Bean
    ObjectWithSchemaCreator objectWithSchemaCreator() {
        return new ObjectWithSchemaCreator();
    }

    @Bean
    PaginatedResponseBuilderCreator paginatedResponseBuilderCreator() {
        return new PaginatedResponseBuilderCreator();
    }

    @Bean
    ListResponseBuilderCreator listResponseBuilderCreator() {
        return new ListResponseBuilderCreator();
    }

    @Bean
    HyperSchemaCreator hyperSchemaCreator(ObjectWithSchemaCreator objectWithSchemaCreator,
            JsonHyperSchemaCreator jsonHyperSchemaCreator) {
        return new HyperSchemaCreator(objectWithSchemaCreator, jsonHyperSchemaCreator);
    }

    @Bean
    @Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)
    LinkFactoryContext linkFactoryContext(FieldCheckerForSchema fieldCheckerForSchema,
            MethodCheckerForLink methodCheckerForLink) throws URISyntaxException {

        URI baseUri = new URI("http://localhost:9998");

        return new LinkFactoryContextDefault(baseUri, methodCheckerForLink, fieldCheckerForSchema);
    }
}