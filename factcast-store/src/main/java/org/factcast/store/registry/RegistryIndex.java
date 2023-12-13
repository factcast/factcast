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
package org.factcast.store.registry;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import lombok.Data;
import org.factcast.store.registry.http.ValidationConstants;
import org.factcast.store.registry.transformation.TransformationSource;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.springframework.core.io.AbstractResource;

/**
 * Object representation for an index.json
 *
 * @author uwe
 */
@Data
public class RegistryIndex {
  @JsonProperty private List<SchemaSource> schemes = new LinkedList<>();

  @JsonProperty private List<TransformationSource> transformations = new LinkedList<>();

  public static Optional<RegistryIndex> fetch(AbstractResource fileSystemResource) {
    try {
      if (fileSystemResource.exists()) {
        return Optional.of(
            ValidationConstants.JACKSON.readValue(
                fileSystemResource.getInputStream(), RegistryIndex.class));
      } else {
        throw new SchemaRegistryUnavailableException(
            new FileNotFoundException("Resource " + fileSystemResource + " does not exist."));
      }
    } catch (IOException e) {
      throw new SchemaRegistryUnavailableException(e);
    }
  }
}
