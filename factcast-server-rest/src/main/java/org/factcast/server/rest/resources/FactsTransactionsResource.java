package org.factcast.server.rest.resources;

import java.util.List;
import java.util.stream.Collectors;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;

import org.factcast.core.DefaultFact;
import org.factcast.core.Fact;
import org.factcast.core.store.FactStore;
import org.factcast.server.rest.resources.cache.NoCache;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mercateo.common.rest.schemagen.JerseyResource;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The resource for writing new facts into the store packed into a transaction
 * 
 * @author joerg_adler
 *
 */
@Path("transactions")
@Slf4j
@Component
@AllArgsConstructor
public class FactsTransactionsResource implements JerseyResource {
    private final FactStore factStore;

    private ObjectMapper objectMapper;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @NoCache
    public void newTransaction(@NotNull @Valid FactTransactionJson factTransactionJson) {

        List<Fact> listToPublish = factTransactionJson.facts().stream().map(f -> {
            if (f.payload().isNull()) {
                throw new BadRequestException("the payload has to be not null");
            }
            String headerString;
            try {
                headerString = objectMapper.writeValueAsString(f.header());
            } catch (JsonProcessingException e) {
                // this should really never happen :-)
                log.error("error", e);
                throw new WebApplicationException(500);
            }
            return DefaultFact.of(headerString, f.payload().toString());
        }).collect(Collectors.toList());

        try {
            factStore.publish(listToPublish);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }
}
