package org.factcast.store.registry.http;

import okhttp3.*;
import org.factcast.itests.factus.Application;
import org.factcast.store.StoreConfigurationProperties;
import org.factcast.store.registry.metrics.RegistryMetrics;
import org.factcast.store.registry.transformation.TransformationStore;
import org.factcast.store.registry.validation.schema.SchemaStore;
import org.factcast.test.AbstractFactCastIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.net.URL;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@ContextConfiguration(classes = {Application.class})
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
class HttpSchemaRegistryITest extends AbstractFactCastIntegrationTest {

  OkHttpClient okHttpClient = mock(OkHttpClient.class);
  Call call = mock(Call.class);
  @Autowired
  RegistryMetrics registryMetrics;
  @Autowired
  SchemaStore schemaStore;
  @Autowired
  TransformationStore transformationStore;
  @Autowired
  StoreConfigurationProperties properties;

  @Test
  public void retryWith503() throws IOException {
    HttpIndexFetcher indexFetcher = new HttpIndexFetcher(new URL("http://test"), okHttpClient, registryMetrics);
    HttpRegistryFileFetcher registryFileFetcher = new HttpRegistryFileFetcher(new URL("http://test"), okHttpClient, registryMetrics);
    HttpSchemaRegistry uut = new HttpSchemaRegistry(
        schemaStore,
        transformationStore,
        indexFetcher,
        registryFileFetcher,
        registryMetrics,
        properties
    );
    when(okHttpClient.newCall(any())).thenReturn(call);
    when(call.execute())
        //.thenReturn(new Response.Builder().code(503).build())
        .thenReturn(new Response.Builder().code(200).body(
            ResponseBody.create(MediaType.parse("application/json"), "{\"schemes\": [], \"transformations\": []}"
            )
        ).build());
    uut.fetchInitial();


  }

}