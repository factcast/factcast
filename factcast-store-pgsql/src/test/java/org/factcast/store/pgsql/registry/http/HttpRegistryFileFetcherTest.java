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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Tags;
import java.net.URL;
import okhttp3.OkHttpClient;
import org.factcast.core.TestHelper;
import org.factcast.store.pgsql.registry.NOPRegistryMetrics;
import org.factcast.store.pgsql.registry.RegistryFileFetchException;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics.EVENT;
import org.factcast.store.pgsql.registry.metrics.RegistryMetrics.OP;
import org.factcast.store.pgsql.registry.metrics.SupplierWithException;
import org.factcast.store.pgsql.registry.transformation.TransformationSource;
import org.factcast.store.pgsql.registry.validation.schema.SchemaSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class HttpRegistryFileFetcherTest {
  @Mock private URL baseUrl;

  @Mock private OkHttpClient client;

  @Spy private final RegistryMetrics registryMetrics = new NOPRegistryMetrics();

  @Test
  public void testCreateSchemaUrl() throws Exception {
    String id = "foo.json";
    URL base = new URL("https://www.ibm.com/registry/");
    URL createSchemaUrl = new URL(base, id);

    assertEquals("https://www.ibm.com/registry/foo.json", createSchemaUrl.toString());
  }

  @Test
  public void testNullContracts() {
    HttpRegistryFileFetcher uut = new HttpRegistryFileFetcher(baseUrl, client, registryMetrics);
    TestHelper.expectNPE(() -> new HttpRegistryFileFetcher(null, registryMetrics));
    TestHelper.expectNPE(() -> new HttpRegistryFileFetcher(baseUrl, null));
    TestHelper.expectNPE(
        () -> new HttpRegistryFileFetcher(null, new OkHttpClient(), registryMetrics));
    TestHelper.expectNPE(() -> new HttpRegistryFileFetcher(null, null, registryMetrics));
    TestHelper.expectNPE(() -> new HttpRegistryFileFetcher(new URL("http://ibm.com"), null));
    TestHelper.expectNPE(() -> uut.fetchSchema(null));
  }

  @Test
  public void testFetchThrowsOn404() throws Exception {
    try (TestHttpServer s = new TestHttpServer()) {
      URL baseUrl = new URL("http://localhost:" + s.port() + "/registry/");
      HttpRegistryFileFetcher uut = new HttpRegistryFileFetcher(baseUrl, registryMetrics);

      assertThrows(
          RegistryFileFetchException.class,
          () -> uut.fetchSchema(new SchemaSource("unknown", "123", "ns", "type", 8)));

      verify(registryMetrics)
          .timed(
              eq(OP.FETCH_REGISTRY_FILE),
              eq(RegistryFileFetchException.class),
              any(SupplierWithException.class));
      verify(registryMetrics)
          .count(
              EVENT.REGISTRY_FILE_FETCH_FAILED,
              Tags.of(RegistryMetrics.TAG_STATUS_CODE_KEY, "404"));
    }
  }

  @Test
  public void testFetchSucceedsOnExampleSchema() throws Exception {
    try (TestHttpServer s = new TestHttpServer()) {

      String json = "{\"foo\":\"bar\"}";

      s.get(
          "/registry/someId",
          ctx -> {
            ctx.res.setStatus(200);
            ctx.res.getWriter().write(json);
          });

      URL baseUrl = new URL("http://localhost:" + s.port() + "/registry/");
      HttpRegistryFileFetcher uut = new HttpRegistryFileFetcher(baseUrl, new NOPRegistryMetrics());
      String fetch = uut.fetchSchema(new SchemaSource("someId", "123", "ns", "type", 8));

      assertEquals(json, fetch);
    }
  }

  @Test
  public void testFetchSucceedsOnExampleTransformation() throws Exception {
    try (TestHttpServer s = new TestHttpServer()) {

      String json = "{\"foo\":\"bar\"}";

      s.get(
          "/registry/someId",
          ctx -> {
            ctx.res.setStatus(200);
            ctx.res.getWriter().write(json);
          });

      URL baseUrl = new URL("http://localhost:" + s.port() + "/registry/");
      HttpRegistryFileFetcher uut = new HttpRegistryFileFetcher(baseUrl, registryMetrics);

      String fetch =
          uut.fetchTransformation(new TransformationSource("someId", "hash", "ns", "type", 8, 2));

      assertEquals(json, fetch);

      verify(registryMetrics)
          .timed(
              eq(OP.FETCH_REGISTRY_FILE),
              eq(RegistryFileFetchException.class),
              any(SupplierWithException.class));
    }
  }
}
