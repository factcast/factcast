package org.factcast.server.rest.resources;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.factcast.core.spec.FactSpec;
import org.factcast.core.subscription.SubscriptionRequestTO;
import org.junit.Test;

public class SubscriptionRequestParams0Test {

    @Test
    public void testToRequest() throws Exception {
        SubscriptionRequestParams subscriptionRequestParams = new SubscriptionRequestParams();
        List<FactSpec> factSpecs = new ArrayList<>();
        FactSpec factSpec = mock(FactSpec.class);
        factSpecs.add(factSpec);
        subscriptionRequestParams.factSpec(factSpecs);
        subscriptionRequestParams.follow(true);
        UUID id = UUID.randomUUID();
        subscriptionRequestParams.from(id.toString());
        SubscriptionRequestTO to = subscriptionRequestParams.toRequest(true);
        assertEquals(factSpecs, to.specs());
        assertTrue(to.continous());
        assertTrue(to.idOnly());
        assertEquals(id, to.startingAfter().get());
    }

}
