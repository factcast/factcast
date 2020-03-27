/*
 * Copyright © 2017-2020 factcast.org
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
package org.factcast.store.pgsql.registry.transformation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransformationSource {
    @JsonProperty(required = true)
    private String id;

    @JsonProperty(required = true)
    private String ns;

    @JsonProperty(required = true)
    private String type;

    // needs a default
    @JsonProperty
    private String hash = "none";

    @JsonProperty(required = true)
    private Integer from;

    @JsonProperty(required = true)
    private Integer to;

    public TransformationKey toKey() {
        return TransformationKey.of(ns, type);
    }

    public boolean isSynthetic() {
        return id.startsWith("synthetic/");
    }
}
