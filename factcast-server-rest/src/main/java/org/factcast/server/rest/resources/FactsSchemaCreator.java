package org.factcast.server.rest.resources;

import static com.mercateo.common.rest.schemagen.util.OptionalUtil.collect;

import java.util.Optional;

import javax.ws.rs.core.Link;

import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;

@Component
@AllArgsConstructor
class FactsSchemaCreator {

    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    private final HyperSchemaCreator hyperSchemaCreator;

    ObjectWithSchema<FactJson> forFactWithId(FactJson returnValue) {
        Optional<Link> selfLink = factsResourceLinkFactory.forCall(Rel.SELF, r -> r.getForId(
                returnValue.header().id().toString()));

        return hyperSchemaCreator.create(returnValue, collect(selfLink));
    }
}
