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

	private final LinkFactory<EventsResource> eventsResourceLinkFactory;

	ObjectWithSchema<Void> forRoot() {
		val getEventsLink = eventsResourceLinkFactory.forCall(EventsRel.EVENTS, r -> r.getServerSentEvents(null));
		val createLink = eventsResourceLinkFactory.forCall(EventsRel.CREATE_TRANSACTIONAL, r -> r.newTransaction(null));

		return hyperSchemaCreator.create(null, collect(getEventsLink, createLink));
	}
}
