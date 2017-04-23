package org.factcast.server.rest.resources;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

import javax.ws.rs.core.Link;
import javax.ws.rs.core.MediaType;

import org.factcast.core.Fact;
import org.factcast.core.subscription.FactStoreObserver;
import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.OutboundEvent;

import com.mercateo.common.rest.schemagen.JsonHyperSchema;
import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.NonNull;

public class EventObserver implements FactStoreObserver {
	private final EventOutput eventOutput;
	private LinkFactory<EventsResource> linkFatory;

	public EventObserver(@NonNull EventOutput eventOutput, @NonNull LinkFactory<EventsResource> linkFatory) {
		this.eventOutput = eventOutput;
		this.linkFatory = linkFatory;
	}

	@Override
	public void onNext(Fact f) {
		UUID t = f.id();

		final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
		eventBuilder.name("new-event");
		String toReturn = t.toString();
		Optional<Link> linkToEvent = linkFatory.forCall(Rel.CANONICAL, r -> r.getForId(toReturn));
		ObjectWithSchema<EventIdJson> withSchema = ObjectWithSchema.create(new EventIdJson(toReturn),
				JsonHyperSchema.from(linkToEvent));
		eventBuilder.data(withSchema);
		eventBuilder.mediaType(MediaType.APPLICATION_JSON_TYPE);
		final OutboundEvent event = eventBuilder.build();
		try {
			eventOutput.write(event);
		} catch (IOException e) {
			throw new RuntimeException("Error when writing the event.", e);
		}
	}

	@Override
	public void onCatchup() {
		final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
		eventBuilder.name("catchup");
		eventBuilder.comment("Signal event for catching up");
		eventBuilder.data("{\"catchup\":true}");
		final OutboundEvent event = eventBuilder.build();

		try {
			eventOutput.write(event);
		} catch (IOException e) {
			throw new RuntimeException("Error when writing the event.", e);
		}
	}

	@Override
	public void onComplete() {

		final OutboundEvent.Builder eventBuilder = new OutboundEvent.Builder();
		eventBuilder.name("complete");
		eventBuilder.comment("Signal event for catching up");
		eventBuilder.data("{\"complete\":true}");
		final OutboundEvent event = eventBuilder.build();
		try {
			eventOutput.write(event);
			eventOutput.close();
		} catch (IOException e) {
			throw new RuntimeException("Error when writing the event.", e);
		}
	}
}