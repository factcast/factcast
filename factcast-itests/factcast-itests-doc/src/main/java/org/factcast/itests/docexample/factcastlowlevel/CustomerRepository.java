package org.factcast.itests.docexample.factcastlowlevel;

import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CustomerRepository {

    @Autowired
    FactCast factCast;

    public Set<String> getCustomerEmails() {
        // TODO is this too naive or is it ok for a demo case?
        var subscriptionRequest = SubscriptionRequest
                .catchup(FactSpec.ns("user").type("CustomerAdded"))
                .or(FactSpec.ns("user").type("CustomerEmailChanged"))
                .or(FactSpec.ns("user").type("CustomerRemoved"))
                .fromScratch();

        var projection = new CustomerEmailsProjection();
        factCast.subscribe(subscriptionRequest, projection::dispatchFacts).awaitComplete();
        return projection.getCustomerEmails();
    }
}
