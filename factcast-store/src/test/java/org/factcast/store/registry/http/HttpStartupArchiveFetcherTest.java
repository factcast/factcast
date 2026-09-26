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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.javacrumbs.shedlock.core.LockProvider;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationKey;
import org.factcast.store.registry.transformation.store.InMemTransformationStoreImpl;
import org.factcast.store.registry.validation.schema.SchemaKey;
import org.factcast.store.registry.validation.schema.store.InMemSchemaStoreImpl;
import org.junit.jupiter.api.Test;

class HttpStartupArchiveFetcherTest {
  private static final String INDEX =
      "{\"schemes\":[{\"id\":\"ns/type/1/schema.json\",\"ns\":\"ns\",\"type\":\"type\",\"version\":1,\"hash\":\"abc\"}],\"transformations\":[{\"id\":\"ns/type/1-2/transform.js\",\"ns\":\"ns\",\"type\":\"type\",\"from\":1,\"to\":2,\"hash\":\"def\"}]}";
  private static final String SCHEMA = "{}";
  private static final String TRANSFORMATION = "function transform(event) { return event; }";

  @Test
  void loadsArchiveAtStartupAndUsesIndividualFilesOnRefresh() throws Exception {
    AtomicInteger archiveRequests = new AtomicInteger();
    AtomicInteger indexRequests = new AtomicInteger();
    AtomicInteger schemaRequests = new AtomicInteger();
    AtomicInteger transformationRequests = new AtomicInteger();
    byte[] archive =
        zip(
            Map.of(
                "index.json", INDEX,
                "ns/type/1/schema.json", SCHEMA,
                "ns/type/1-2/transform.js", TRANSFORMATION));

    try (TestHttpServer server =
        server(
            archive, 200, archiveRequests, indexRequests, schemaRequests, transformationRequests)) {
      HttpSchemaRegistry registry = registry(server, true);
      registry.fetchInitial();

      assertEquals(1, archiveRequests.get());
      assertEquals(0, indexRequests.get());
      assertEquals(0, schemaRequests.get());
      assertEquals(0, transformationRequests.get());
      assertTrue(registry.get(SchemaKey.of("ns", "type", 1)).isPresent());
      assertEquals(1, registry.get(TransformationKey.of("ns", "type")).size());

      registry.refresh();
      assertEquals(1, archiveRequests.get());
      assertEquals(1, indexRequests.get());
      assertEquals(1, schemaRequests.get());
      assertEquals(1, transformationRequests.get());
    }
  }

  @Test
  void fallsBackWhenArchiveIsMissingOrInvalid() throws Exception {
    assertFallback(null, 404);
    assertFallback("not a zip".getBytes(StandardCharsets.UTF_8), 200);
    assertFallback(zip(Map.of("index.json", INDEX)), 200);
    assertFallback(zip(Map.of("../outside", "unsafe", "index.json", INDEX)), 200);
  }

  @Test
  void leavesDefaultHttpBehaviorUnchanged() throws Exception {
    AtomicInteger archiveRequests = new AtomicInteger();
    AtomicInteger indexRequests = new AtomicInteger();
    AtomicInteger schemaRequests = new AtomicInteger();
    AtomicInteger transformationRequests = new AtomicInteger();
    try (TestHttpServer server =
        server(null, 404, archiveRequests, indexRequests, schemaRequests, transformationRequests)) {
      registry(server, false).fetchInitial();
      assertEquals(0, archiveRequests.get());
      assertEquals(1, indexRequests.get());
      assertEquals(1, schemaRequests.get());
      assertEquals(1, transformationRequests.get());
    }
  }

  private static void assertFallback(byte[] archive, int status) throws Exception {
    AtomicInteger archiveRequests = new AtomicInteger();
    AtomicInteger indexRequests = new AtomicInteger();
    AtomicInteger schemaRequests = new AtomicInteger();
    AtomicInteger transformationRequests = new AtomicInteger();
    try (TestHttpServer server =
        server(
            archive,
            status,
            archiveRequests,
            indexRequests,
            schemaRequests,
            transformationRequests)) {
      HttpSchemaRegistry registry = registry(server, true);
      registry.fetchInitial();
      assertTrue(registry.get(SchemaKey.of("ns", "type", 1)).isPresent());
      assertEquals(1, archiveRequests.get());
      assertEquals(1, indexRequests.get());
      assertEquals(1, schemaRequests.get());
      assertEquals(1, transformationRequests.get());
    }
  }

  private static HttpSchemaRegistry registry(TestHttpServer server, boolean zipEnabled)
      throws Exception {
    RegistryMetrics metrics = new NOPRegistryMetrics();
    StoreConfigurationProperties properties =
        new StoreConfigurationProperties()
            .setPersistentRegistry(false)
            .setSchemaRegistryZipEnabled(zipEnabled);
    return new HttpSchemaRegistry(
        URI.create("http://localhost:" + server.port() + "/registry/").toURL(),
        new InMemSchemaStoreImpl(metrics),
        new InMemTransformationStoreImpl(metrics),
        metrics,
        properties,
        mock(LockProvider.class));
  }

  private static TestHttpServer server(
      byte[] archive,
      int archiveStatus,
      AtomicInteger archiveRequests,
      AtomicInteger indexRequests,
      AtomicInteger schemaRequests,
      AtomicInteger transformationRequests) {
    return new TestHttpServer(
        config -> {
          config.routes.get(
              "/registry/registry.zip",
              ctx -> {
                archiveRequests.incrementAndGet();
                ctx.res().setStatus(archiveStatus);
                if (archive != null) {
                  ctx.res().getOutputStream().write(archive);
                }
              });
          config.routes.get(
              "/registry/index.json",
              ctx -> {
                indexRequests.incrementAndGet();
                ctx.res().getOutputStream().write(INDEX.getBytes(StandardCharsets.UTF_8));
              });
          config.routes.get(
              "/registry/ns/type/1/schema.json",
              ctx -> {
                schemaRequests.incrementAndGet();
                ctx.res().getOutputStream().write(SCHEMA.getBytes(StandardCharsets.UTF_8));
              });
          config.routes.get(
              "/registry/ns/type/1-2/transform.js",
              ctx -> {
                transformationRequests.incrementAndGet();
                ctx.res().getOutputStream().write(TRANSFORMATION.getBytes(StandardCharsets.UTF_8));
              });
        });
  }

  private static byte[] zip(Map<String, String> files) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
      for (Map.Entry<String, String> file : files.entrySet()) {
        archive.putNextEntry(new ZipEntry(file.getKey()));
        archive.write(file.getValue().getBytes(StandardCharsets.UTF_8));
        archive.closeEntry();
      }
    }
    return bytes.toByteArray();
  }
}
