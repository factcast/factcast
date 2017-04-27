package org.factcast.core.wellknown;

import java.util.UUID;

import org.factcast.core.Fact;

import lombok.AccessLevel;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.FieldDefaults;

/**
 * Special fact without payload, that can be used to identify marks in the
 * stream in order to build sync. interfaces on top of eventual consistent
 * views.
 *
 * @author uwe.schaefer@mercateo.com
 *
 */
// TODO open ns to be defined by constructor?
@Value
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class MarkFact implements Fact {

    public static final String TYPE = "_mark";

    public static final String NS = "_";

    UUID id = UUID.randomUUID();

    String ns = NS;

    String type = TYPE;

    UUID aggId = null;

    /**
     * does not have any payload.
     */
    @Override
    @NonNull
    public String jsonPayload() {
        return "{}";
    }

    @Override
    public String jsonHeader() {
        return String.format("{\"ns\":\"%s\",\"id\":\"%s\",\"type\":\"%s\"}", ns(), id(), type());
    }

    @Override
    public String meta(String key) {
        return null;
    }

}
