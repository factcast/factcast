package org.factcast.server.rest.resources;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.JerseyResource;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;

@Path("/")
@Component
@AllArgsConstructor
public class RootResource implements JerseyResource {

	private final RootSchemaCreator schemaCreator;

	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public ObjectWithSchema<Void> getRoot() {
		return schemaCreator.forRoot();

	}
}