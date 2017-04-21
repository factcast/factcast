package org.factcast.server.rest.resources;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;

import org.factcast.core.store.subscription.ClientSubscriptionRequest;
import org.factcast.core.store.subscription.FactSpec;
import org.factcast.core.store.subscription.SubscriptionRequestTO;
import org.factcast.core.store.subscription.SubscriptionRequest;

import lombok.Data;

@Data
public class SubscriptionRequestParams {
	@QueryParam("ns")
	@NotNull
	private String ns;
	@QueryParam("since")
	private String since;
	@QueryParam("follow")
	private boolean follow;
	@QueryParam("aggId")
	private String aggId;

	// FIXME TODO JAR: Type missing?
	// FIXME TODO JAR: List of FactSpecs...

	public SubscriptionRequest toRequest() {

		SubscriptionRequestTO r = new SubscriptionRequestTO();
		r.continous(follow);
		if (since != null) {
			r.startingAfter(UUID.fromString(since));
		}
		
		return r;

//		SpecBuilder builder;
//		FactSpec factSpec = FactSpec.ns(ns);
//		if (aggId != null) {
//			factSpec.aggId(UUID.fromString(aggId));
//		}
//		if (follow) {
//			builder = ClientSubscriptionRequest.follow(factSpec);
//		} else {
//			builder = ClientSubscriptionRequest.catchup(factSpec);
//		}
//		if (since != null) {
//			return builder.since(UUID.fromString(since));
//		} else {
//			return builder.sinceInception();
//		}
	}
}
