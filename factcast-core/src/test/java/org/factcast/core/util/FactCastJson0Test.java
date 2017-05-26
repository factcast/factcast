package org.factcast.core.util;

import static org.factcast.core.TestHelper.*;
import static org.junit.Assert.*;

import org.junit.Test;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class FactCastJson0Test {

    @Test(expected = NullPointerException.class)
    public void testCopyNull() throws Exception {
        FactCastJson.copy(null);
    }

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

    @Test
    public void testReadValueNull() throws Exception {
        expectNPE(() -> FactCastJson.readValue(null, ""));
        expectNPE(() -> FactCastJson.readValue(null, null));
        expectNPE(() -> FactCastJson.readValue(FactCastJson.class, null));
    }

    @Test
    public void testWriteValueNull() throws Exception {
        expectNPE(() -> FactCastJson.writeValueAsString(null));
    }

}
