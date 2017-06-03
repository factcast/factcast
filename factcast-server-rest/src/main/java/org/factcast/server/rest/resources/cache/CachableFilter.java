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
 * Filter for inserting a HTTP-Header for caching the response for 100000
 * seconds
 * 
 * @author joerg_adler
 *
 */
@Cacheable
@Priority(Priorities.HEADER_DECORATOR)
public class CachableFilter implements ContainerResponseFilter {

    @Override
    public void filter(@NonNull ContainerRequestContext requestContext,
            @NonNull ContainerResponseContext responseContext) throws IOException {
        if (responseContext.getStatusInfo().getStatusCode() == 200) {
            responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL,
                    "max-age=1000000, s-maxage=1000000, public");
        } else {
            responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL,
                    "max-age=10, s-maxage=10, public");
        }
    }

}
