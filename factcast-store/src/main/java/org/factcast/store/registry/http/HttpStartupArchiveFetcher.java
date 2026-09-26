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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.factcast.store.registry.IndexFetcher;
import org.factcast.store.registry.RegistryFileFetcher;
import org.factcast.store.registry.RegistryIndex;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationSource;
import org.factcast.store.registry.validation.schema.SchemaSource;
import org.springframework.core.io.FileSystemResource;

/** Uses a downloaded registry archive only during the initial HTTP registry fetch. */
@Slf4j
final class HttpStartupArchiveFetcher implements IndexFetcher, RegistryFileFetcher {
  private final URL baseUrl;
  private final OkHttpClient client;
  private final HttpIndexFetcher httpIndexFetcher;
  private final HttpRegistryFileFetcher httpFileFetcher;

  private boolean initialFetch;
  private Path archiveRoot;

  HttpStartupArchiveFetcher(@NonNull URL baseUrl, @NonNull RegistryMetrics metrics) {
    this(baseUrl, ValidationConstants.OK_HTTP, metrics);
  }

  HttpStartupArchiveFetcher(
      @NonNull URL baseUrl, @NonNull OkHttpClient client, @NonNull RegistryMetrics metrics) {
    this.baseUrl = baseUrl;
    this.client = client;
    this.httpIndexFetcher = new HttpIndexFetcher(baseUrl, client, metrics);
    this.httpFileFetcher = new HttpRegistryFileFetcher(baseUrl, client, metrics);
  }

  void beginInitialFetch() {
    initialFetch = true;
  }

  void endInitialFetch() {
    initialFetch = false;
    discardArchive();
  }

  @Override
  public Optional<RegistryIndex> fetchIndex() {
    if (initialFetch) {
      try {
        return Optional.of(downloadAndValidateArchive());
      } catch (Exception e) {
        log.warn(
            "Could not load schema registry archive; falling back to individual HTTP files", e);
        discardArchive();
      }
    }
    return httpIndexFetcher.fetchIndex();
  }

  @Override
  public String fetchSchema(SchemaSource key) throws IOException {
    if (archiveRoot != null) {
      return Files.readString(resolveEntry(archiveRoot, key.id()), StandardCharsets.UTF_8);
    }
    return httpFileFetcher.fetchSchema(key);
  }

  @Override
  public String fetchTransformation(TransformationSource key) throws IOException {
    if (archiveRoot != null) {
      return Files.readString(resolveEntry(archiveRoot, key.id()), StandardCharsets.UTF_8);
    }
    return httpFileFetcher.fetchTransformation(key);
  }

  private RegistryIndex downloadAndValidateArchive() throws IOException {
    Path root = Files.createTempDirectory("factcast-registry-");
    archiveRoot = root;
    URL archiveUrl = new URL(baseUrl, "registry.zip");
    Request request = new Request.Builder().url(archiveUrl).build();
    try (Response response = client.newCall(request).execute()) {
      if (response.code() != ValidationConstants.HTTP_OK || response.body() == null) {
        throw new IOException("Could not download " + archiveUrl + ": HTTP " + response.code());
      }
      try (InputStream stream = response.body().byteStream();
          ZipInputStream archive = new ZipInputStream(stream)) {
        ZipEntry entry;
        while ((entry = archive.getNextEntry()) != null) {
          Path target = resolveEntry(root, entry.getName());
          if (entry.isDirectory()) {
            Files.createDirectories(target);
          } else {
            Files.createDirectories(target.getParent());
            Files.copy(archive, target);
          }
          archive.closeEntry();
        }
      }
    }

    RegistryIndex index =
        RegistryIndex.fetch(new FileSystemResource(root.resolve("index.json")))
            .orElseThrow(() -> new IOException("Archive is missing index.json"));
    for (SchemaSource schema : index.schemes()) {
      requireFile(root, schema.id());
    }
    for (TransformationSource transformation : index.transformations()) {
      if (!transformation.isSynthetic()) {
        requireFile(root, transformation.id());
      }
    }
    return index;
  }

  private static void requireFile(Path root, String id) throws IOException {
    if (!Files.isRegularFile(resolveEntry(root, id))) {
      throw new IOException("Archive is missing registry file " + id);
    }
  }

  private static Path resolveEntry(Path root, String name) throws IOException {
    if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")) {
      throw new IOException("Unsafe registry archive path: " + name);
    }
    Path target = root.resolve(name).normalize();
    if (target.equals(root) || !target.startsWith(root)) {
      throw new IOException("Unsafe registry archive path: " + name);
    }
    return target;
  }

  private void discardArchive() {
    Path root = archiveRoot;
    archiveRoot = null;
    if (root != null) {
      try (Stream<Path> files = Files.walk(root)) {
        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
          Files.deleteIfExists(file);
        }
      } catch (IOException e) {
        log.warn("Could not remove extracted schema registry archive at {}", root, e);
      }
    }
  }
}
