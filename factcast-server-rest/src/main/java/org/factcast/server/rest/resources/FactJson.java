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

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.mercateo.common.rest.schemagen.IgnoreInRestSchema;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Value;

/**
 * return object for the resources returning facts to the client. Also used in
 * the new transactions in the payload
 * 
 * @author joerg_adler
 *
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FactJson {

    @JsonProperty
    @NotNull
    @NonNull
    @Valid
    private Header header;

    @JsonProperty
    @NotNull
    @NonNull
    private JsonNode payload;

    @Value
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Header {

        @JsonProperty
        @NotNull
        final UUID id;

        @JsonProperty
        @NotNull
        final String ns;

        @JsonProperty
        final String type;

        @JsonProperty
        final Set<UUID> aggIds;

        @JsonProperty
        final Map<String, String> meta = new HashMap<>();

        @JsonAnySetter
        @IgnoreInRestSchema
        final Map<String, Object> anyOther = new HashMap<>();

        @JsonAnyGetter
        public Map<String, Object> anyOther() {
            return anyOther;
        }
    }
}
