package org.factcast.server.rest;

import java.util.Set;
import java.util.UUID;

import org.assertj.core.util.Sets;
import org.factcast.core.Fact;

public class TestFacts {
    public static final Fact one = new Fact() {

        private final UUID id = UUID.randomUUID();

        private final Set<UUID> aggId = Sets.newLinkedHashSet(UUID.randomUUID());

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
                    + "\"aggIds\":[\"" + aggId.iterator().next() + "\"],"//
                    + "\"id\":\"" + id + "\""//
                    + "}";
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public Set<UUID> aggId() {
            return aggId;
        }

        @Override
        public String meta(String key) {
            return null;
        }
    };
}
