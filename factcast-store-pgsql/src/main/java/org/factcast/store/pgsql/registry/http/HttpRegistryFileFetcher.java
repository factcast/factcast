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

import com.google.common.annotations.VisibleForTesting;
import io.micrometer.core.instrument.Tags;
import java.io.IOException;
import java.net.URL;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.factcast.store.pgsql.registry.RegistryFileFetchException;
import org.factcast.store.pgsql.registry.RegistryFileFetcher;
import org.factcast.store.pgsql.registry.metrics.MetricEvent;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.TimedOperation;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;

/**
 * fetches one schema from the registry according to its coordinates (SchemaKey)
 *
 * @author uwe
 */
@Slf4j
public class HttpRegistryFileFetcher implements RegistryFileFetcher {

  @NonNull private final URL baseUrl;

  @NonNull private final OkHttpClient client;

  @NonNull private final RegistryMetrics registryMetrics;

  public HttpRegistryFileFetcher(@NonNull URL baseUrl, @NonNull RegistryMetrics registryMetrics) {
    this(baseUrl, ValidationConstants.OK_HTTP, registryMetrics);
  }

  @VisibleForTesting
  HttpRegistryFileFetcher(
      @NonNull URL baseUrl,
      @NonNull OkHttpClient client,
      @NonNull RegistryMetrics registryMetrics) {
    this.client = client;
    this.baseUrl = baseUrl;
    this.registryMetrics = registryMetrics;
  }

  @Override
  public String fetchTransformation(TransformationSource key) throws IOException {
    String id = key.id();
    URL url = new URL(baseUrl, id);
    log.debug("Fetching Transformation {}", key.id());

    return registryMetrics.timed(
        TimedOperation.FETCH_REGISTRY_FILE, RegistryFileFetchException.class, () -> fetch(url));
  }

  @Override
  public String fetchSchema(SchemaSource key) throws IOException {
    String id = key.id();

    URL url = new URL(baseUrl, id);
    log.debug("Fetching Schema {}", key.id());

    return registryMetrics.timed(
        TimedOperation.FETCH_REGISTRY_FILE, RegistryFileFetchException.class, () -> fetch(url));
  }

  private String fetch(URL url) throws RegistryFileFetchException {
    Request req = new Request.Builder().url(url).build();

    try (Response response = client.newCall(req).execute()) {

      if (response.code() != ValidationConstants.HTTP_OK) {
        registryMetrics.count(
            MetricEvent.REGISTRY_FILE_FETCH_FAILED,
            Tags.of(RegistryMetrics.TAG_STATUS_CODE_KEY, String.valueOf(response.code())));

        throw new RegistryFileFetchException(url, response.code(), response.message());
      } else {
        return response.body().string();
      }
    } catch (Exception e) {
      throw new RegistryFileFetchException(url, 0, e.getMessage());
    }
  }
}
