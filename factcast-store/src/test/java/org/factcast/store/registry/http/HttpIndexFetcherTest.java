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
import static org.mockito.Mockito.*;

import io.micrometer.core.instrument.Tags;
import java.net.URL;
import java.util.Date;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import okhttp3.*;
import okhttp3.Response.Builder;
import org.factcast.store.registry.NOPRegistryMetrics;
import org.factcast.store.registry.SchemaRegistryUnavailableException;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.junit.jupiter.api.*;

public class HttpIndexFetcherTest {
  private HttpIndexFetcher uut;

  @Test
  void testFetchUsesIfModifiedSince() throws Exception {

    try (TestHttpServer s = new TestHttpServer()) {
      String etag = "123";
      String since = new Date().toString();

      s.get(
          "/registry/index.json",
          ctx -> {
            Map<String, String> headers = ctx.headerMap();
            String etagHeader = headers.get(ValidationConstants.HTTPHEADER_E_TAG);
            String sinceHeader = headers.get(ValidationConstants.HTTPHEADER_IF_MODIFIED_SINCE);

            if (etag.equals(etagHeader) && since.equals(sinceHeader)) {
              ctx.res.setStatus(304);
            } else {
              HttpServletResponse res = ctx.res;
              res.setStatus(200);
              res.getWriter()
                  .write(
                      "\n"
                          + "{\"schemes\":[{\"id\":\"namespaceA/eventA/1/schema.json\",\"ns\":\"namespaceA\",\"type\":\"eventA\",\"version\":1,\"hash\":\"84e69a2d3e3d195abb986aad22b95ffd\"},{\"id\":\"namespaceA/eventA/2/schema.json\",\"ns\":\"namespaceA\",\"type\":\"eventA\",\"version\":2,\"hash\":\"24d48268356e3cb7ac2f148850e4aac1\"}]}");
              res.addHeader(ValidationConstants.HTTPHEADER_E_TAG, etag);
              res.addHeader(ValidationConstants.HTTPHEADER_LAST_MODIFIED, since);
            }
          });

      URL baseUrl = new URL("http://localhost:" + s.port() + "/registry");
      uut = new HttpIndexFetcher(baseUrl, new NOPRegistryMetrics());
      assertTrue(uut.fetchIndex().isPresent());
      assertFalse(uut.fetchIndex().isPresent());
      assertFalse(uut.fetchIndex().isPresent());
    }
  }

  @Test
  void testThrowsExceptionOn404() throws Exception {
    try (TestHttpServer s = new TestHttpServer()) {

      s.get("/registry/index.json", ctx -> ctx.res.setStatus(404));

      var registryMetrics = mock(RegistryMetrics.class);

      URL baseUrl = new URL("http://localhost:" + s.port() + "/registry");
      uut = new HttpIndexFetcher(baseUrl, registryMetrics);

      assertThrows(SchemaRegistryUnavailableException.class, () -> uut.fetchIndex());

      verify(registryMetrics, atLeastOnce())
          .count(
              RegistryMetrics.EVENT.SCHEMA_REGISTRY_UNAVAILABLE,
              Tags.of(RegistryMetrics.TAG_STATUS_CODE_KEY, "404"));
    }
  }

  @SneakyThrows
  @Test
  void retries() {
    Call call = mock(Call.class);

    OkHttpClient client = mock(OkHttpClient.class);
    when(client.newCall(any())).thenReturn(call);

    uut = new HttpIndexFetcher(new URL("http://ibm.com"), client, mock(RegistryMetrics.class));

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
            .body(
                ResponseBody.create(
                    MediaType.parse("application/json"),
                    "{\"schemes\": [], \"transformations\": []}"))
            .build();

    when(call.execute())
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(fail)
        .thenReturn(success);

    uut.fetchIndex();
  }
}
