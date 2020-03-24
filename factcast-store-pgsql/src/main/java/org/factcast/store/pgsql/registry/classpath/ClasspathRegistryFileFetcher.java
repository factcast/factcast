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
package org.factcast.store.pgsql.registry.classpath;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;

import org.factcast.store.pgsql.registry.RegistryFileFetcher;
import org.factcast.store.pgsql.registry.SchemaRegistryUnavailableException;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.springframework.core.io.ClassPathResource;

import com.google.common.io.Files;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClasspathRegistryFileFetcher implements RegistryFileFetcher {

    private final @NonNull String base;

    @Override
    public String fetchTransformation(TransformationSource key) throws IOException {
        String path = base + "/" + key.ns() + "/" + key.type() + "/" + key.from() + "-" + key.to()
                + "/transform.js";
        return fetch(path);

    }

    private String fetch(String path) {
        try {
            File file = new ClassPathResource(path).getFile();
            if (file.exists()) {
                return readFile(file);
            } else {
                throw new SchemaRegistryUnavailableException(new FileNotFoundException("Resource "
                        + path
                        + " does not exist."));
            }
        } catch (IOException e) {
            throw new SchemaRegistryUnavailableException(e);
        }
    }

    static String readFile(File file)
            throws IOException {
        // ok, this is dirty
        List<String> readLines = Files.readLines(file, Charset.defaultCharset());
        return String.join("\n", readLines);
    }

    @Override
    public String fetchSchema(SchemaSource key) throws IOException {
        String path = base + "/" + key.ns() + "/" + key.type() + "/" + key.version()
                + "/schema.json";
        return fetch(path);
    }

}
