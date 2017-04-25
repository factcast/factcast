package org.factcast.server.rest.resources;

import java.util.Optional;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;

import com.mercateo.common.rest.schemagen.JerseyResource;
import com.mercateo.common.rest.schemagen.JsonHyperSchema;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkMetaFactory;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

@Path("/")

public class RootResource implements JerseyResource {

	@Inject
	private LinkMetaFactory linkMetaFactory;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public ObjectWithSchema<Void> getRoot() {
		LinkFactory<EventsResource> eventsResourceFactory = linkMetaFactory.createFactoryFor(EventsResource.class);
		Optional<Link> eventsLink = eventsResourceFactory.forCall(EventsRel.EVENTS,
				r -> r.getServerSentEvents(null));

		Optional<Link> createLink = eventsResourceFactory
				.forCall(EventsRel.CREATE_TRANSACTIONAL, r -> r.newTransaction(null));

		return ObjectWithSchema.create(null, JsonHyperSchema.from(eventsLink, createLink));

	}
}