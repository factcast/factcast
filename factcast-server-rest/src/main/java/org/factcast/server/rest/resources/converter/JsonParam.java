package org.factcast.server.rest.resources.converter;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Marker annotation, that the query parameter is an JSON entity and should be
 * unmarshalled
 * 
 * @author joerg_adler
 *
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonParam {

}
