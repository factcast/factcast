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

import lombok.AllArgsConstructor;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.SubscriptionRequestTO;
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
import org.springframework.stereotype.Component;

@Path("events")
@Component
@AllArgsConstructor
public class EventsResource implements JerseyResource {

	private final FactStore readFactStore;

	private final EventsSchemaCreator schemaCreator;

	private final EventObserverFactory eventObserverFactory;

	@GET
	@Produces(SseFeature.SERVER_SENT_EVENTS)
	@NoCache
	public EventOutput getServerSentEvents(
			@NotNull @Valid @BeanParam SubscriptionRequestParams subscriptionRequestParams) {
		final EventOutput eventOutput = new EventOutput();
		SubscriptionRequestTO req = subscriptionRequestParams.toRequest();
		FactStoreObserver observer = eventObserverFactory.createFor(eventOutput);
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
		return schemaCreator.forFactWithId(returnValue, id);
	}
}
