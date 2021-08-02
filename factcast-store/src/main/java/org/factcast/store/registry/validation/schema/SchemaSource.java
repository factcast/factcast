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
package org.factcast.store.registry.validation.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.annotations.VisibleForTesting;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * defines a source of a schema
 *
 * @author uwe
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SchemaSource {
  @JsonProperty(required = true)
  private String id;

  @JsonProperty(required = true)
  private String hash;

  @JsonProperty(required = true)
  private String ns;

  @JsonProperty(required = true)
  private String type;

  @JsonProperty(required = true)
  private int version;

  public SchemaKey toKey() {
    return SchemaKey.of(ns, type, version);
  }

  @VisibleForTesting
  public SchemaSource(String id, String hash, String ns, String type, int version) {
    this.id = id;
    this.hash = hash;
    this.ns = ns;
    this.type = type;
    this.version = version;
  }
}
