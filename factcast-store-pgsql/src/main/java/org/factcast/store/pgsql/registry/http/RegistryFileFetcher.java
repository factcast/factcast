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
package org.factcast.store.pgsql.registry.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;

import com.google.common.annotations.VisibleForTesting;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * fetches one schema from the registry according to its coordinates (SchemaKey)
 *
 * @author uwe
 */

@Slf4j
public class RegistryFileFetcher {

    @NonNull
    private final URL baseUrl;

    @NonNull
    private final OkHttpClient client;

    public RegistryFileFetcher(@NonNull URL baseUrl) {
        this(baseUrl, ValidationConstants.client);
    }

    @VisibleForTesting
    RegistryFileFetcher(@NonNull URL baseUrl, @NonNull OkHttpClient client) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    public String fetchTransformation(TransformationSource key) throws IOException {
        URL url = createFileUrl(baseUrl, key.id());
        log.debug("Fetching Transformation {}", key.id());

        return fetch(url);
    }

    public String fetchSchema(SchemaSource key) throws IOException {
        URL url = createFileUrl(baseUrl, key.id());
        log.debug("Fetching Schema {}", key.id());

        return fetch(url);
    }

    private String fetch(URL url) throws IOException {
        Request req = new Request.Builder().url(url).build();

        try (Response response = client.newCall(req).execute()) {

            String responseBodyAsText = response.body().string();

            if (response.code() != ValidationConstants.HTTP_OK) {
                throw new RegistryFileFetchException(url, response.code(), response.message());
            } else {
                return responseBodyAsText;
            }
        }

    }

    protected URL createFileUrl(@NonNull URL base, @NonNull String id)
            throws MalformedURLException {

        String externalForm = base.toExternalForm();
        StringBuilder sb = new StringBuilder(externalForm);
        if (!externalForm.endsWith("/"))
            sb.append("/");
        sb.append(id);
        return new URL(sb.toString());
    }
}
