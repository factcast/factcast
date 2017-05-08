package org.factcast.server.rest.resources;

import java.util.List;
import java.util.UUID;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.QueryParam;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.server.rest.resources.converter.JsonParam;
import org.glassfish.jersey.media.sse.SseFeature;
import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionRequestParams {
    @HeaderParam(SseFeature.LAST_EVENT_ID_HEADER)
    private String from;

    @QueryParam("follow")
    private boolean follow;

    @NotEmpty
    @QueryParam("factSpec")
    @JsonParam
    private List<FactSpec> factSpec;

    public SubscriptionRequestTO toRequest() {

        SubscriptionRequestTO r = new SubscriptionRequestTO();
        r.continous(follow);
        if (from != null) {
            r.startingAfter(UUID.fromString(from));
        }

        r.addSpecs(factSpec);
        return r;
    }
}
