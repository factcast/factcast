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
public class UserEmailsProjection {

    private final Map<UUID, String> userEmails = new HashMap<>();

    @NonNull
    public Set<String> getUserEmails() {
        return new HashSet<>(userEmails.values());
    }

    public void apply(Fact fact) {
        switch (fact.type()) {
            case "UserAdded":
                handleUserAdded(fact);
                break;
            case "UserRemoved":
                handleUserRemoved(fact);
                break;
            default:
                log.error("Fact type {} not supported", fact.type());
                break;
        }
    }

    @VisibleForTesting
    void handleUserAdded(Fact fact) {
        JsonNode payload = parsePayload(fact);
        userEmails.put(extractIdFrom(payload), extractEmailFrom(payload));
    }

    @VisibleForTesting
    void handleUserRemoved(Fact fact) {
        JsonNode payload = parsePayload(fact);
        userEmails.remove(extractIdFrom(payload));
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
