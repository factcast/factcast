package org.factcast.server.rest.resources.cache;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;

import lombok.NonNull;

/**
 * Filter for inserting a HTTP-Header for not caching the response seconds
 * 
 * @author joerg_adler
 *
 */
@NoCache
@Priority(Priorities.HEADER_DECORATOR)
public class NoCacheFilter implements ContainerResponseFilter {

    @Override
    public void filter(@NonNull ContainerRequestContext requestContext,
            @NonNull ContainerResponseContext responseContext) throws IOException {
        responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "no-cache");
    }

}
