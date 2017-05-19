package org.factcast.server.rest.resources;

import java.util.List;

import javax.validation.Valid;

import org.hibernate.validator.constraints.NotEmpty;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class FactTransactionJson {
    @JsonProperty
    @NotEmpty
    @Valid
    private List<FactJson> facts;
}
