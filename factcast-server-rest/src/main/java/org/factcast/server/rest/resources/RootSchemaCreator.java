package org.factcast.server.rest.resources;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;
import lombok.AllArgsConstructor;
import lombok.val;
import org.springframework.stereotype.Component;

import static com.mercateo.common.rest.schemagen.util.OptionalUtil.collect;

@Component
@AllArgsConstructor
class RootSchemaCreator {
	private final HyperSchemaCreator hyperSchemaCreator;

	private final LinkFactory<EventsResource> eventsResourceLinkFactory;

	ObjectWithSchema<Void> forRoot() {
		val getEventsLink = eventsResourceLinkFactory.forCall(EventsRel.EVENTS, r -> r
				.getServerSentEvents(null));

		return hyperSchemaCreator.create(null, collect(getEventsLink));
	}
}
