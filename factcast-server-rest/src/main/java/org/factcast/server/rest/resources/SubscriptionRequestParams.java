package org.factcast.server.rest.resources;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import javax.ws.rs.HeaderParam;
import javax.ws.rs.QueryParam;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.factcast.server.rest.resources.converter.JsonParam;
import org.glassfish.jersey.media.sse.SseFeature;
import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mercateo.common.rest.schemagen.IgnoreInRestSchema;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * parameter object for SSE subscriptions
 * 
 * @author joerg_adler
 *
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@Slf4j
public class SubscriptionRequestParams {
    private static final AtomicLong counter = new AtomicLong();

    @HeaderParam(SseFeature.LAST_EVENT_ID_HEADER)
    @IgnoreInRestSchema
    private String from;

    @HeaderParam("Debug-Info")
    @IgnoreInRestSchema
    private String debugInfo;

    @QueryParam("continuous")
    private boolean continuous;

    @NotEmpty
    @QueryParam("factSpec")
    @JsonParam
    private List<FactSpec> factSpec;

    public SubscriptionRequestTO toRequest(boolean idOnly) {

        SubscriptionRequestTO r = new SubscriptionRequestTO();
        r.continuous(continuous);
        if (from != null) {
            r.startingAfter(UUID.fromString(from));
        }

        r.addSpecs(factSpec);
        r.idOnly(idOnly);

        r.debugInfo(convertDebugInfo());
        return r;
    }

    private String convertDebugInfo() {
        String innerDebugString = "rest-sub#" + counter.incrementAndGet();
        log.debug("Subscription " + toString() + " converted to " + innerDebugString);
        return innerDebugString;
    }
}
