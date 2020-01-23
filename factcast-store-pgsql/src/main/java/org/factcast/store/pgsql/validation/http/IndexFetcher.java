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
import java.util.Optional;

import org.factcast.store.pgsql.validation.schema.SchemaRegistryUnavailableException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;

/**
 * fetches the index (using if-modified-since)
 *
 * @author uwe
 *
 */
@Slf4j
@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_SINGLETON)
@RequiredArgsConstructor
public class IndexFetcher {

    private final URL schemaRegistryUrl;

    private String since;

    private String etag;

    /**
     * @return future for empty, if index is unchanged, otherwise the updated
     *         index.
     */
    Optional<RegistryIndex> fetchIndex() {

        try {
            URL indexUrl = createIndexUrl(schemaRegistryUrl);

            Builder req = new Request.Builder().url(indexUrl);
            if (since != null) {
                req.addHeader(ValidationConstants.HTTPHEADER_IF_MODIFIED_SINCE, since);
            }
            if (etag != null) {
                req.addHeader(ValidationConstants.HTTPHEADER_E_TAG, etag);
            }

            Request request = req.build();
            log.debug("Fetching index from {}", request.url());
            try (Response response = ValidationConstants.client.newCall(request).execute()) {

                String responseBodyAsText = response.body().string();

                if (response.code() == 304) {
                    // we're done here.
                    return Optional.empty();
                } else if (response.code() == 200) {

                    this.etag = response.header(ValidationConstants.HTTPHEADER_E_TAG);
                    this.since = response.header(ValidationConstants.HTTPHEADER_LAST_MODIFIED);

                    RegistryIndex readValue = ValidationConstants.objectMapper.readValue(
                            responseBodyAsText,
                            RegistryIndex.class);
                    return Optional.of(readValue);

                } else
                    throw new SchemaRegistryUnavailableException(request.url(), response.code(),
                            response.message());
            }
        } catch (IOException e) {
            throw new SchemaRegistryUnavailableException(e);
        }

    }

    private URL createIndexUrl(URL schemaRegistryUrl2) throws MalformedURLException {
        String spec = schemaRegistryUrl + "/index.json";

        return new URL(spec.replaceAll("//", "/"));
    }
}
