package org.factcast.server.rest.resources;

import java.util.UUID;

import javax.validation.constraints.NotNull;
import javax.ws.rs.QueryParam;

import org.factcast.core.subscription.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;

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

	public SubscriptionRequestTO toRequest() {

		SubscriptionRequestTO r = new SubscriptionRequestTO();
		r.continous(follow);
		if (since != null) {
			r.startingAfter(UUID.fromString(since));
		}

		FactSpec factSpec = FactSpec.ns(ns);
		if (aggId != null) {
			factSpec = factSpec.aggId(UUID.fromString(aggId));
		}
		r.addSpec(factSpec);
		return r;
	}
}
