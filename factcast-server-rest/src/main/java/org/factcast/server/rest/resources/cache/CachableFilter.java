package org.factcast.server.rest.resources.cache;

import java.io.IOException;

import javax.annotation.Priority;
import javax.ws.rs.Priorities;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.HttpHeaders;

@Cacheable
@Priority(Priorities.HEADER_DECORATOR)
public class CachableFilter implements ContainerResponseFilter {

	@Override
	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException {
		responseContext.getHeaders().putSingle(HttpHeaders.CACHE_CONTROL, "max-age=1000000, s-maxage=1000000, public");
	}

}
