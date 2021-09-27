package org.factcast.itests.docexample.factcastlowlevel;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.annotations.VisibleForTesting;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;

import java.util.*;


@Slf4j
public class CustomerEmailsProjection {

    private final Map<UUID, String> customerEmails = new HashMap<>();

    @NonNull
    public Set<String> getCustomerEmails() {
        return new HashSet<>(customerEmails.values());
    }

    public void apply(Fact fact) {
        switch (fact.type()) {
            case "CustomerAdded":
                handleCustomerAdded(fact);
                break;
            case "CustomerEmailChanged":
                handleCustomerEmailChanged(fact);
                break;
            case "CustomerRemoved":
                handleCustomerRemoved(fact);
                break;
            default:
                log.error("Fact type {} not supported", fact.type());
                break;
        }
    }

    @VisibleForTesting
    void handleCustomerAdded(Fact fact) {
        JsonNode payload = parsePayload(fact);
        customerEmails.put(extractIdFrom(payload), extractEmailFrom(payload));
    }

    @VisibleForTesting
    void handleCustomerEmailChanged(Fact fact) {
        JsonNode payload = parsePayload(fact);
        customerEmails.put(extractIdFrom(payload), extractEmailFrom(payload));
    }

    @VisibleForTesting
    void handleCustomerRemoved(Fact fact) {
        JsonNode payload = parsePayload(fact);
        customerEmails.remove(extractIdFrom(payload));
    }

    // helper methods:

    @SneakyThrows
    private JsonNode parsePayload(Fact fact) {
        return FactCastJson.readTree(fact.jsonPayload());
    }

    private UUID extractIdFrom(JsonNode payload) {
        return UUID.fromString(payload.get("id").asText());
    }
    private String extractEmailFrom(JsonNode payload) {
        return payload.get("email").asText();
    }
}
