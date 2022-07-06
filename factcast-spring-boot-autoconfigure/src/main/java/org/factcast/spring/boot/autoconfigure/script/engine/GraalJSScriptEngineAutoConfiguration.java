package org.factcast.spring.boot.autoconfigure.script.engine;

import org.factcast.script.engine.EngineCache;
import org.factcast.script.engine.graaljs.GraalJSEngineCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({EngineCache.class, GraalJSEngineCache.class})
public class GraalJSScriptEngineAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(EngineCache.class)
  public EngineCache engineCache() {
    return new GraalJSEngineCache();
  }
}
