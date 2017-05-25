package org.factcast.server.rest;

import java.util.Set;
import java.util.UUID;

import org.assertj.core.util.Sets;
import org.factcast.core.Fact;

public class TestFacts {
    public static final Fact one = new Fact() {

        private final UUID id = UUID.fromString("d941087a-cb63-400f-bca2-7b65214fafa4");

        private final Set<UUID> aggIds = Sets.newLinkedHashSet(UUID.randomUUID());

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
            return "{\"id\":\"" + id + "\"}";
        }

        @Override
        public String jsonHeader() {

            return "{\"id\":\"" + id + "\"," //
                    + "\"ns\":\"a\","//
                    + "\"type\":\"a\","//
                    + "\"aggIds\":[\"" + aggIds.iterator().next() + "\"]"//
                    + "}";
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public Set<UUID> aggIds() {
            return aggIds;
        }

        @Override
        public String meta(String key) {
            return null;
        }
    };
}
