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
package org.factcast.store.registry.http;

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Tags;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Optional;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import org.factcast.store.registry.IndexFetcher;
import org.factcast.store.registry.RegistryIndex;
import org.factcast.store.registry.SchemaRegistryUnavailableException;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

/**
 * fetches the index (using if-modified-since)
 *
 * @author uwe
 */
@Slf4j
public class HttpIndexFetcher implements IndexFetcher {

  private final OkHttpClient client;

  private final RegistryMetrics registryMetrics;

  private final RetryTemplate retry =
      new RetryTemplateBuilder()
          .retryOn(SchemaRegistryUnavailableException.class)
          .exponentialBackoff(50, 1.5, 500)
          .maxAttempts(10)
          .build();

  public HttpIndexFetcher(@NonNull URL baseUrl, @NonNull RegistryMetrics registryMetrics) {
    this(baseUrl, ValidationConstants.OK_HTTP, registryMetrics);
  }

  @VisibleForTesting
  protected HttpIndexFetcher(
      @NonNull URL baseUrl,
      @NonNull OkHttpClient client,
      @NonNull RegistryMetrics registryMetrics) {
    this.client = client;
    schemaRegistryUrl = baseUrl;
    this.registryMetrics = registryMetrics;
  }

  private final URL schemaRegistryUrl;

  private String since;

  private String etag;

  /** @return future for empty, if index is unchanged, otherwise the updated index. */
  @Override
  public Optional<RegistryIndex> fetchIndex() {
    return retry.execute(
        ctx -> {
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
            try (Response response = client.newCall(request).execute()) {

              String responseBodyAsText = response.body().string();

              if (response.code() == ValidationConstants.HTTP_NOT_MODIFIED) {
                // we're done here.
                return Optional.empty();
              } else {
                if (response.code() == ValidationConstants.HTTP_OK) {

                  etag = response.header(ValidationConstants.HTTPHEADER_E_TAG);
                  since = response.header(ValidationConstants.HTTPHEADER_LAST_MODIFIED);

                  RegistryIndex readValue =
                      ValidationConstants.JACKSON.readValue(
                          responseBodyAsText, RegistryIndex.class);
                  return Optional.of(readValue);

                } else {
                  registryMetrics.count(
                      RegistryMetrics.EVENT.SCHEMA_REGISTRY_UNAVAILABLE,
                      Tags.of(
                          RegistryMetrics.TAG_STATUS_CODE_KEY, String.valueOf(response.code())));

                  throw new SchemaRegistryUnavailableException(
                      request.url().toString(), response.code(), response.message());
                }
              }
            }

          } catch (Exception e) {
            registryMetrics.count(RegistryMetrics.EVENT.SCHEMA_REGISTRY_UNAVAILABLE);
            throw new SchemaRegistryUnavailableException(e);
          }
        });
  }

  private URL createIndexUrl(URL url) throws MalformedURLException {
    String spec = url + "/index.json";
    return new URL(spec.replaceAll("//", "/"));
  }
}
