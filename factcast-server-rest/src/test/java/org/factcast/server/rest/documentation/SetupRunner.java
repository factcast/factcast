package org.factcast.server.rest.documentation;

import java.util.Arrays;
import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.NonNull;

@Component
public class SetupRunner {
    static final Fact one = new Fact() {

        private final UUID id = UUID.randomUUID();

        private final UUID aggId = UUID.randomUUID();

        @Override
        public String type() {
            return "a";
        }

        @Override
        public String ns() {
            return "a";
        }

        @Override
        public String jsonPayload() {
            return "{}";
        }

        @Override
        public String jsonHeader() {

            return "{\"ns\":\"a\","//
                    + "\"type\":\"a\","//
                    + "\"aggId\":\"" + aggId + "\","//
                    + "\"id\":\"" + id + "\""//
                    + "}";
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public UUID aggId() {
            return aggId;
        }

        @Override
        public String meta(String key) {
            return null;
        }
    };

    @Autowired
    public SetupRunner(@NonNull FactStore factStore) {
        factStore.publish(Arrays.asList(one));
    }

}
