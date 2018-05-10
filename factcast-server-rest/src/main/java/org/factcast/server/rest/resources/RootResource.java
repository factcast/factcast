/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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