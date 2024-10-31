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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.factcast.store.registry.transformation.TransformationSource;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.springframework.core.io.AbstractResource;

/**
 * Abstract superclass for RegistryFileFetcher that operates on local files.
 */
@RequiredArgsConstructor
public abstract class AbstractFileBasedRegistryFileFetcher implements RegistryFileFetcher {

    @Override
    public String fetchTransformation(@NonNull TransformationSource key) {
        String subPath = buildTransformationPath(key);
        return fetch(subPath);
    }

    @Override
    public String fetchSchema(@NonNull SchemaSource key) {
        String subPath = buildSchemaPath(key);
        return fetch(subPath);
    }

    private @NonNull String fetch(String subPath) {
        try {
            AbstractResource file = getFile(subPath);
            if (file.exists()) {
                return file.getContentAsString(StandardCharsets.UTF_8);
            } else {
                throw new SchemaRegistryUnavailableException(
                    new FileNotFoundException("Resource " + subPath + " does not exist."));
            }
        } catch (IOException e) {
            throw new SchemaRegistryUnavailableException("Error fetching resource: " + subPath, e);
        }
    }

    private String buildTransformationPath(TransformationSource key) {
        return key.ns() + "/" + key.type() + "/" + key.from() + "-" + key.to() + "/transform.js";
    }

    private String buildSchemaPath(SchemaSource key) {
        return key.ns() + "/" + key.type() + "/" + key.version() + "/schema.json";
    }

    /**
     * Gets the file resource for the specified subPath.
     *
     * @param subPath the sub path of a file, relative to some context
     * @return an AbstractResource pointing to the requested file
     * @throws IOException in case of problems resolving the AbstractResource
     */
    protected abstract AbstractResource getFile(String subPath) throws IOException;
}
