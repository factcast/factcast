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
package org.factcast.store.pgsql.registry.filesystem;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.factcast.store.pgsql.registry.RegistryFileFetcher;
import org.factcast.store.pgsql.registry.SchemaRegistryUnavailableException;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class FilesystemRegistryFileFetcher implements RegistryFileFetcher {

    private final @NonNull String base;

    @Override
    public String fetchTransformation(@NonNull TransformationSource key) {
        String subPath = key.ns() + "/" + key.type() + "/" + key.from() + "-" + key.to()
                + "/transform.js";
        return fetch(subPath);

    }

    @Override
    public String fetchSchema(@NonNull SchemaSource key) {
        String subPath = key.ns() + "/" + key.type() + "/" + key.version()
                + "/schema.json";
        return fetch(subPath);
    }

    private @NonNull String fetch(String path) {
        try {
            File file = new File(base, path);
            if (file.exists()) {
                return readFile(file);
            } else {
                throw new SchemaRegistryUnavailableException(
                        new FileNotFoundException("Resource "
                                + path
                                + " does not exist."));
            }
        } catch (IOException e) {
            throw new SchemaRegistryUnavailableException(e);
        }
    }

    private static @NonNull String readFile(@NonNull File file)
            throws IOException {
        StringBuilder sb = new StringBuilder();
        java.nio.file.Files.lines(file.toPath()).forEachOrdered(l -> {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(l);
        });
        return sb.toString();
    }

}
