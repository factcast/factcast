package org.factcast.core;

import java.util.Set;
import java.util.UUID;

import lombok.NonNull;

/**
 * Defines a fact to be either published or consumed. Consists of two JSON
 * Strings: jsonHeader and jsonPayload. Also provides convenience getters for
 * id,ns,type and aggId.
 *
 * Only generated code, does not need unit testing.
 *
 * @author uwe.schaefer@mercateo.com
 *
 */
// TODO add schema
public interface Fact {

    @NonNull
    UUID id();

    @NonNull
    String ns();

    String type();

    Set<UUID> aggIds();

    @NonNull
    String jsonHeader();

    @NonNull
    String jsonPayload();

    String meta(String key);

    default Long metaAsLong(String key) {
        String s = meta(key);
        if (s != null) {
            return Long.valueOf(s);
        }
        return null;
    }

    // hint to where to get the default from
    static Fact of(@NonNull String jsonHeader, @NonNull String jsonPayload) {
        return DefaultFact.of(jsonHeader, jsonPayload);
    }

    default boolean before(Fact other) {
        Long mySer = metaAsLong("_ser");
        Long otherSer = other.metaAsLong("_ser");

        if (mySer == null || otherSer == null) {
            throw new IllegalStateException("'_ser' Meta attribute not found");
        }

        return mySer < otherSer;
    }
}
