package org.factcast.server.rest.resources;

import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.server.rest.resources.cache.Cacheable;
import org.factcast.server.rest.resources.cache.NoCache;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;

import com.mercateo.common.rest.schemagen.JerseyResource;
import com.mercateo.common.rest.schemagen.JsonHyperSchema;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkMetaFactory;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

@Path("events")
public class EventsResource implements JerseyResource {

	@Inject
	private FactStore readFactStore;

	@Inject
	private LinkMetaFactory linkMetaFactory;

	@GET
	@Produces(SseFeature.SERVER_SENT_EVENTS)
	@NoCache
	public EventOutput getServerSentEvents(
			@NotNull @Valid @BeanParam SubscriptionRequestParams subscriptionRequestParams) {
		final EventOutput eventOutput = new EventOutput();
		LinkFactory<EventsResource> linkFatory = linkMetaFactory.createFactoryFor(EventsResource.class);
		SubscriptionRequest req = subscriptionRequestParams.toRequest();
		FactStoreObserver observer = new EventObserver(eventOutput, linkFatory);
		readFactStore.subscribe(req, observer);
		return eventOutput;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{id}")
	@Cacheable
	public ObjectWithSchema<FactJson> getForId(@NotNull @PathParam("id") String id) {
		Optional<Fact> fact = readFactStore.fetchById(UUID.fromString(id));
		FactJson returnValue = fact.map(f -> new FactJson(f.jsonHeader(), f.jsonPayload()))
				.orElseThrow(NotFoundException::new);
		Optional<Link> selfLink = linkMetaFactory.createFactoryFor(EventsResource.class).forCall(Rel.SELF,
				r -> r.getForId(id));
		return ObjectWithSchema.create(returnValue, JsonHyperSchema.from(selfLink));
	}
}
