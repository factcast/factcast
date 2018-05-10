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

import javax.ws.rs.WebApplicationException;

import org.factcast.core.Fact;
import org.factcast.core.util.FactCastJson;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.AllArgsConstructor;

/**
 * this class maps facts from the underlying store to the JSON return objects
 * 
 * @author joerg_adler
 *
 */
@AllArgsConstructor
@Component
public class FactTransformer {

    private final ObjectMapper objectMapper;

    public FactJson toJson(Fact f) {
        try {
            JsonNode payLoad = objectMapper.readTree(f.jsonPayload());
            return new FactJson(FactCastJson.readValue(
                    org.factcast.server.rest.resources.FactJson.Header.class, f.jsonHeader()),
                    payLoad);
        } catch (Exception e) {
            throw new WebApplicationException(e.getMessage(), 500);
        }
    }
}
