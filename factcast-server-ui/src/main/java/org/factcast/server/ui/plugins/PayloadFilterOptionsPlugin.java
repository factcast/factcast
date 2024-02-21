package org.factcast.server.ui.plugins;

import org.factcast.core.Fact;

import java.util.List;
import java.util.UUID;

public class PayloadFilterOptionsPlugin extends JsonViewPlugin{
    @Override
    protected boolean isReady() {
        return true;
    }

    @Override
    protected void doHandle(Fact fact, JsonPayload payload, JsonEntryMetaData jsonEntryMetaData) {
        final var idPaths = payload.findPaths("$..*").stream()
                .filter(p -> p.toLowerCase().endsWith("id']"))
                .toList();
        idPaths.forEach(p -> {
            try {
                final var uuid = payload.read(p, UUID.class);
                jsonEntryMetaData.addPayloadAggregateIdFilterOption(p, uuid);
            } catch (Exception e) {
                // maybe not a uuid, silently ignore
            }
        });
    }
}
