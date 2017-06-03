package org.factcast.server.rest.resources;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.NonNull;
import lombok.Value;

/**
 * return object with only id in it
 * 
 * @author joerg_adler
 *
 */
@Value
public class FactIdJson {

    @NonNull
    @JsonProperty
    private String id;
}
