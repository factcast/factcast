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
package org.factcast.store.pgsql.validation.http;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import org.factcast.store.pgsql.validation.schema.SchemaSource;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * fetches one schema from the registry according to its coordinates (SchemaKey)
 *
 * @author uwe
 *
 */
@RequiredArgsConstructor
@Slf4j
public class SchemaFetcher {
    private static final OkHttpClient client = new OkHttpClient();

    @NonNull
    private final URL baseUrl;

    public String fetch(SchemaSource key) throws IOException {

        URL url = createSchemaUrl(baseUrl, key);
        Request req = new Request.Builder().url(url).build();
        log.info("Fetching {}", key.id());
        try (Response response = client.newCall(req).execute()) {

            String responseBodyAsText = response.body().string();

            if (response.code() != 200) {
                throw new SchemaFetchException(url, response.code(), response.message());
            } else {
                return responseBodyAsText;
            }
        }

    }

    private URL createSchemaUrl(@NonNull URL base, SchemaSource key) throws MalformedURLException {
        String spec = base + "/" + key.id();
        return new URL(spec.replaceAll("//", "/"));

    }
}
