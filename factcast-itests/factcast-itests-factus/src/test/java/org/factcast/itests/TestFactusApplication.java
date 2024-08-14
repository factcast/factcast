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
package org.factcast.itests;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.factcast.factus.serializer.JacksonSnapshotSerializer;
import org.factcast.factus.serializer.SnapshotSerializer;
import org.factcast.spring.boot.autoconfigure.snap.InMemoryAndDiskSnapshotCacheAutoConfiguration;
import org.factcast.spring.boot.autoconfigure.snap.InMemorySnapshotCacheAutoConfiguration;
import org.factcast.spring.boot.autoconfigure.snap.RedissonSnapshotCacheAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@SuppressWarnings("ALL")
@SpringBootApplication
@Slf4j
@EnableAutoConfiguration(
    exclude = {
      CompositeMeterRegistryAutoConfiguration.class,
      RedissonSnapshotCacheAutoConfiguration.class,
      InMemorySnapshotCacheAutoConfiguration.class,
      InMemoryAndDiskSnapshotCacheAutoConfiguration.class,
    })
public class TestFactusApplication {

  public static void main(String[] args) {
    SpringApplication.run(TestFactusApplication.class, args);
  }

  @Bean
  public SnapshotSerializer snapshotSerializer() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    return new JacksonSnapshotSerializer(mapper);
  }

  @Bean
  @Primary
  public MeterRegistry meterRegistry() {
    return new SimpleMeterRegistry();
  }
}
