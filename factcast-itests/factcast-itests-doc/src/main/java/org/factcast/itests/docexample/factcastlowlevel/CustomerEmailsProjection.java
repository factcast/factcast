package org.factcast.itests.docexample.factcastlowlevel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.factcast.core.Fact;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class CustomerEmailsProjection {

    private ObjectMapper objectMapper = new ObjectMapper();

    private Map<UUID, String> customerEmails = new HashMap<>();

    public Set<String> getCustomerEmails() {
        return new HashSet<>(customerEmails.values());
    }

    public void dispatchFacts(Fact fact) {
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

    private void handleCustomerAdded(Fact fact) {
        var payload = parsePayload(fact);
        customerEmails.put(getCustomerId(payload), payload.get("email").asText());
    }

    private void handleCustomerEmailChanged(Fact fact) {
        var payload = parsePayload(fact);
        customerEmails.put(getCustomerId(payload), payload.get("email").asText());
    }

    private void handleCustomerRemoved(Fact fact) {
        var payload = parsePayload(fact);
        customerEmails.remove(getCustomerId(payload));
    }

    @SneakyThrows
    private JsonNode parsePayload(Fact fact) {
        return objectMapper.readTree(fact.jsonPayload());
    }

    private UUID getCustomerId(JsonNode payload) {
        return UUID.fromString(payload.get("id").asText());
    }
}
