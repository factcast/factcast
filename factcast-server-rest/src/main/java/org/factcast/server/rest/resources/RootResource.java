package org.factcast.server.rest.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.factcast.server.rest.resources.cache.NoCache;
import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.JerseyResource;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;

/**
 * entrypoint of the REST-API, delivering the links to the other resources
 * 
 * @author joerg_adler
 *
 */
@Path("/")
@Component
@AllArgsConstructor
public class RootResource implements JerseyResource {

    private final RootSchemaCreator schemaCreator;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @NoCache
    public ObjectWithSchema<Void> getRoot() {
        return schemaCreator.forRoot();

    }
}