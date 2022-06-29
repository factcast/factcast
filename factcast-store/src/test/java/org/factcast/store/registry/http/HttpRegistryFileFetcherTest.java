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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Tags;
import java.net.URL;
import lombok.SneakyThrows;
import okhttp3.*;
import okhttp3.Response.Builder;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.RegistryFileFetchException;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.metrics.SupplierWithException;
import org.factcast.store.registry.transformation.TransformationSource;
import org.factcast.store.registry.validation.schema.SchemaSource;
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
  public void testFetchThrowsOn404() throws Exception {
    try (TestHttpServer s = new TestHttpServer()) {
      URL baseUrl = new URL("http://localhost:" + s.port() + "/registry/");
      var uut = new HttpRegistryFileFetcher(baseUrl, registryMetrics);

      assertThrows(
          RegistryFileFetchException.class,
          () -> uut.fetchSchema(new SchemaSource("unknown", "123", "ns", "type", 8)));

      verify(registryMetrics, atLeastOnce())
          .timed(
              eq(RegistryMetrics.OP.FETCH_REGISTRY_FILE),
              eq(RegistryFileFetchException.class),
              any(SupplierWithException.class));
      verify(registryMetrics, atLeastOnce())
          .count(
              RegistryMetrics.EVENT.REGISTRY_FILE_FETCH_FAILED,
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
      var uut = new HttpRegistryFileFetcher(baseUrl, new NOPRegistryMetrics());
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
      var uut = new HttpRegistryFileFetcher(baseUrl, registryMetrics);

      String fetch =
          uut.fetchTransformation(new TransformationSource("someId", "hash", "ns", "type", 8, 2));

      assertEquals(json, fetch);

      verify(registryMetrics)
          .timed(
              eq(RegistryMetrics.OP.FETCH_REGISTRY_FILE),
              eq(RegistryFileFetchException.class),
              any(SupplierWithException.class));
    }
  }

  @SneakyThrows
  @Test
  void retries() {
    Call call = mock(Call.class);
    OkHttpClient client = mock(OkHttpClient.class);
    HttpRegistryFileFetcher uut =
        new HttpRegistryFileFetcher(new URL("http://ibm.com"), client, registryMetrics);
    when(client.newCall(any())).thenReturn(call);

    Request request = new Request.Builder().url("http://ibm.com").get().build();
    Response fail =
        new Builder()
            .code(503)
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message("slow down, fella")
            .build();
    Response success =
        new Builder()
            .code(200)
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message("ok")
            .body(ResponseBody.create(MediaType.parse("application/json"), "{}"))
            .build();

    when(call.execute())
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(success);

    SchemaSource key = new SchemaSource("id", "hash", "ns", "type", 1);
    uut.fetchSchema(key);
  }
}
