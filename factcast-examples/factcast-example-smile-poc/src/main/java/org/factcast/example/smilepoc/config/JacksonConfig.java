package org.factcast.example.smilepoc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class JacksonConfig {

  @Bean
  @Primary
  ObjectMapper jsonMapper() {
    return new ObjectMapper();
  }

  @Bean(name = "smileMapper")
  ObjectMapper smileMapper() {
    return new ObjectMapper(new SmileFactory());
  }
}
