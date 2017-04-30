package org.factcast.core.util;

import static org.junit.Assert.*;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class FactCastJsonTest {

    @Test
    public void testCopy() throws Exception {
        final Foo foo = new Foo("bar", "baz");
        Foo copy = FactCastJson.copy(foo);

        assertNotSame(foo, copy);
        assertNotEquals(foo, copy);

        assertEquals(foo.bar(), copy.bar());
        assertNull(copy.baz());

    }

    @AllArgsConstructor
    @Data
    @NoArgsConstructor
    static class Foo {
        @JsonProperty
        String bar;

        @JsonIgnore
        String baz;
    }

}
