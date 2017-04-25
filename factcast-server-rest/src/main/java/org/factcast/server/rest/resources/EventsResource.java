package org.factcast.server.rest.resources;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BeanParam;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;

import org.factcast.core.DefaultFact;
import org.factcast.core.DefaultFact.Header;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.core.subscription.FactStoreObserver;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.core.util.FactCastJson;
import org.factcast.server.rest.resources.cache.Cacheable;
import org.factcast.server.rest.resources.cache.NoCache;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseFeature;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercateo.common.rest.schemagen.JerseyResource;
import com.mercateo.common.rest.schemagen.JsonHyperSchema;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.LinkMetaFactory;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.extern.slf4j.Slf4j;

@Path("events")
@Slf4j
public class EventsResource implements JerseyResource {

	@Inject
	private FactStore factStore;

	@Inject
	private LinkMetaFactory linkMetaFactory;

	@Inject
	private ObjectMapper objectMapper;

	@GET
	@Produces(SseFeature.SERVER_SENT_EVENTS)
	@NoCache
	public EventOutput getServerSentEvents(
			@NotNull @Valid @BeanParam SubscriptionRequestParams subscriptionRequestParams) {
		final EventOutput eventOutput = new EventOutput();
		LinkFactory<EventsResource> linkFatory = linkMetaFactory.createFactoryFor(EventsResource.class);
		SubscriptionRequestTO req = subscriptionRequestParams.toRequest();
		FactStoreObserver observer = new EventObserver(eventOutput, linkFatory);
		factStore.subscribe(req, observer);
		return eventOutput;
	}

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("{id}")
	@Cacheable
	public ObjectWithSchema<FactJson> getForId(@NotNull @PathParam("id") String id) {
		Optional<Fact> fact = factStore.fetchById(UUID.fromString(id));
		FactJson returnValue = fact.map(f -> {
			try {
				JsonNode payLoad = objectMapper.readTree(f.jsonPayload());
				return new FactJson(FactCastJson.reader().forType(Header.class).readValue(f.jsonHeader()), payLoad);
			} catch (Exception e) {
				log.error("error", e);
				throw new WebApplicationException(500);
			}
		}).orElseThrow(NotFoundException::new);
		Optional<Link> selfLink = linkMetaFactory.createFactoryFor(EventsResource.class).forCall(Rel.SELF,
				r -> r.getForId(id));
		return ObjectWithSchema.create(returnValue, JsonHyperSchema.from(selfLink));
	}

	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@NoCache
	public void newTransaction(@NotNull @Valid FactTransactionJson factTransactionJson) {

		List<Fact> listToPublish = factTransactionJson.facts().stream().map(f -> {
			String headerString;
			try {
				headerString = objectMapper.writeValueAsString(f.header);
			} catch (Exception e) {
				log.error("error", e);
				throw new WebApplicationException(500);
			}
			return DefaultFact.of(headerString, f.payLoad().toString());
		}).collect(Collectors.toList());

		factStore.publish(listToPublish);
	}
}
