package org.factcast.server.rest.resources;

import static com.mercateo.common.rest.schemagen.util.OptionalUtil.collect;

import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;
import lombok.val;

@Component
@AllArgsConstructor
class RootSchemaCreator {
    private final HyperSchemaCreator hyperSchemaCreator;

    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    private final LinkFactory<FactsTransactionsResource> transactionsLinkFactory;

    ObjectWithSchema<Void> forRoot() {
        val getFactsLink = factsResourceLinkFactory.forCall(FactsRel.FACT_IDS, r -> r
                .getServerSentEvents(null));

        val getFullFactsLink = factsResourceLinkFactory.forCall(FactsRel.FULL_FACTS, r -> r
                .getServerSentEventsFull(null));
        val createLink = transactionsLinkFactory.forCall(FactsRel.CREATE_TRANSACTIONAL, r -> r
                .newTransaction(null));

        return hyperSchemaCreator.create(null, collect(getFactsLink, getFullFactsLink, createLink));
    }
}
