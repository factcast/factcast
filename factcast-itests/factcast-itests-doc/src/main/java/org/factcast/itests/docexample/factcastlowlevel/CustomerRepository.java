package org.factcast.itests.docexample.factcastlowlevel;

import lombok.NonNull;
import org.factcast.core.Fact;
import org.factcast.core.FactCast;
import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequest;
import org.factcast.core.subscription.observer.FactObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class CustomerRepository {

    @Autowired
    FactCast factCast;

    public Set<String> getCustomerEmails() {
        var subscriptionRequest = SubscriptionRequest
                .catchup(FactSpec.ns("user").type("CustomerAdded"))
                .or(FactSpec.ns("user").type("CustomerEmailChanged"))
                .or(FactSpec.ns("user").type("CustomerRemoved"))
                .fromScratch();

        var projection = new CustomerEmailsProjection();
        class FactObserverImpl implements FactObserver {

            @Override
            public void onNext(@NonNull Fact fact) {
                projection.apply(fact);
            }
        }

        factCast.subscribe(subscriptionRequest, new FactObserverImpl()).awaitComplete();
        return projection.getCustomerEmails();
    }
}
