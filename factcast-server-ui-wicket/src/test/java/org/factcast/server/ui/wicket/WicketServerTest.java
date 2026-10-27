/*
 * Copyright © 2017-2026 factcast.org
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
package org.factcast.server.ui.wicket;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.factcast.core.store.FactStore;
import org.factcast.server.security.CommonSecurityConfiguration;
import org.factcast.server.ui.port.FactRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    classes = WicketServerTest.Server.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "factcast.security.enabled=false")
class WicketServerTest {
  @TempDir static Path directory;
  @LocalServerPort int port;
  private HttpClient client;

  @SpringBootConfiguration
  @EnableAutoConfiguration(
      exclude = org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration.class)
  @org.springframework.boot.autoconfigure.ImportAutoConfiguration(WicketUIConfiguration.class)
  @Import(CommonSecurityConfiguration.class)
  static class Server {
    @Bean
    FactStore factStore() {
      var store = mock(FactStore.class);
      when(store.enumerateNamespaces()).thenReturn(Set.of("users"));
      when(store.enumerateTypes("users")).thenReturn(Set.of("UserCreated"));
      when(store.latestSerial()).thenReturn(4L);
      return store;
    }

    @Bean
    io.micrometer.core.instrument.simple.SimpleMeterRegistry registry() {
      return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("factcast.ui.report.store.path", () -> directory.toString());
  }

  @BeforeEach
  void client() {
    client =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
  }

  URI uri(String path) {
    return URI.create("http://localhost:" + port + path);
  }

  HttpResponse<String> get(String path) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  HttpResponse<String> getPage(String path) throws Exception {
    var response = get(path);
    for (int i = 0; response.statusCode() == 302 && i < 5; i++) {
      var location = uri(path).resolve(response.headers().firstValue("Location").orElseThrow());
      response =
          get(
              location.getRawPath()
                  + (location.getRawQuery() == null ? "" : "?" + location.getRawQuery()));
    }
    return response;
  }

  static String csrf(String html) {
    var input = Pattern.compile("<input[^>]*name=\"_csrf\"[^>]*>").matcher(html);
    assertThat(input.find()).as("CSRF hidden input").isTrue();
    var value = Pattern.compile("value=\"([^\"]*)\"").matcher(input.group());
    assertThat(value.find()).isTrue();
    return value.group(1);
  }

  private HttpResponse<String> login() throws Exception {
    var page = get("/login");
    assertThat(page.statusCode()).isEqualTo(200);
    return post(
        "/login",
        "username=alice&password=security_disabled&_csrf="
            + URLEncoder.encode(csrf(page.body()), java.nio.charset.StandardCharsets.UTF_8));
  }

  HttpResponse<String> post(String path, String body) throws Exception {
    return client.send(
        HttpRequest.newBuilder(uri(path))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void startsWicketOnBootAndRequiresLogin() throws Exception {
    assertThat(get("/ui/full").statusCode()).isEqualTo(302);
    assertThat(login().statusCode()).isEqualTo(302);
    var query = getPage("/ui/full");
    assertThat(query.statusCode()).isEqualTo(200);
    assertThat(query.body()).contains("FactCast Server UI", "Starting Serial", "users");
    assertThat(getPage("/ui/id").body()).contains("Fact-ID");
    assertThat(getPage("/ui/report").body()).contains("Your Reports");
  }

  @Test
  void loginRejectsMissingCsrfAndBadCredentials() throws Exception {
    assertThat(post("/login", "username=alice&password=security_disabled").statusCode())
        .isEqualTo(403);
    var page = get("/login");
    var response =
        post(
            "/login",
            "username=alice&password=bad&_csrf="
                + URLEncoder.encode(csrf(page.body()), java.nio.charset.StandardCharsets.UTF_8));
    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location").orElseThrow()).endsWith("/login?error");
  }

  @Test
  void downloadsOnlyTheCurrentUsersFiles() throws Exception {
    Files.createDirectories(directory.resolve("bob"));
    // The username comes from FactCastUser, including in security-disabled mode.
    var accountName = org.factcast.server.security.auth.FactCastAccount.GOD.id();
    Files.createDirectories(directory.resolve(accountName));
    Files.writeString(directory.resolve(accountName).resolve("mine.json"), "{\"events\":[]}");
    Files.writeString(directory.resolve("bob").resolve("other.json"), "secret");
    assertThat(get("/files/mine.json").statusCode()).isEqualTo(302);
    login();
    var download = get("/files/mine.json");
    assertThat(download.statusCode()).isEqualTo(200);
    assertThat(download.body()).isEqualTo("{\"events\":[]}");
    assertThat(get("/files/other.json").statusCode()).isEqualTo(404);
    assertThat(get("/files/../bob/other.json").statusCode()).isIn(400, 404);
  }

  @Test
  void loginRestoresTheOriginalQueryLink() throws Exception {
    String id = "da716582-1fe2-4576-917b-124d3a4ec084";
    String state =
        URLEncoder.encode(
            "{\"id\":\"" + id + "\",\"version\":0}", java.nio.charset.StandardCharsets.UTF_8);
    assertThat(get("/ui/id?state=" + state).statusCode()).isEqualTo(302);
    var response = login();
    assertThat(response.headers().firstValue("Location").orElseThrow()).contains("/ui/id?state=");
  }

  @org.springframework.beans.factory.annotation.Autowired FactRepository repository;

  @org.springframework.beans.factory.annotation.Autowired
  io.micrometer.core.instrument.simple.SimpleMeterRegistry registry;

  @Test
  void recordsRepositoryTiming() {
    repository.latestSerial();
    assertThat(
            registry
                .find(org.factcast.server.ui.metrics.UiMetrics.TIMER_METRIC_NAME)
                .tag("method", "latestSerial")
                .timer())
        .isNotNull();
  }
}
