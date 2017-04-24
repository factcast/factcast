package org.factcast.server.rest.resources;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import lombok.AllArgsConstructor;
import org.glassfish.jersey.media.sse.EventOutput;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
class EventObserverFactory {

	private final LinkFactory<EventsResource> eventsResourceLinkFactory;

	private final HyperSchemaCreator hyperSchemaCreator;

	EventObserver createFor(EventOutput eventOutput) {
		return new EventObserver(eventOutput, eventsResourceLinkFactory, hyperSchemaCreator);
	}
}
