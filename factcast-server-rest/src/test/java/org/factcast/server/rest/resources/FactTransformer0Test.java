package org.factcast.server.rest.resources;

import static org.junit.Assert.assertEquals;

import java.util.Set;
import java.util.UUID;

import javax.ws.rs.WebApplicationException;

import org.factcast.core.Fact;
import org.factcast.server.rest.TestFacts;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import com.fasterxml.jackson.databind.ObjectMapper;

@RunWith(MockitoJUnitRunner.class)
public class FactTransformer0Test {
    @Spy
    private ObjectMapper objectMapper;

    @InjectMocks
    private FactTransformer factTransformer;

    @Test
    public void testToJson() throws Exception {
        FactJson fact = factTransformer.toJson(TestFacts.one);
        assertEquals(TestFacts.one.jsonHeader(), objectMapper.writeValueAsString(fact.header())
                .replace(",\"meta\":{}", ""));
        assertEquals(TestFacts.one.jsonPayload(), objectMapper.writeValueAsString(fact.payload()));
    }

    @Test(expected = WebApplicationException.class)
    public void testExceptionalFact() throws Exception {
        factTransformer.toJson(new Fact() {

            @Override
            public String type() {

                return null;
            }

            @Override
            public String ns() {

                return null;
            }

            @Override
            public String meta(String key) {

                return null;
            }

            @Override
            public String jsonPayload() {
                return "This is no JSON";
            }

            @Override
            public String jsonHeader() {
                return "This is no JSON";
            }

            @Override
            public UUID id() {

                return null;
            }

            @Override
            public Set<UUID> aggIds() {

                return null;
            }
        });
    }

}
