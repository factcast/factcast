/*
 * Copyright © 2017-2022 factcast.org
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
package org.factcast.spring.boot.autoconfigure.script.engine;

import org.factcast.script.engine.EngineFactory;
import org.factcast.script.engine.graaljs.GraalJSEngineCache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass({EngineFactory.class, GraalJSEngineCache.class})
public class GraalJSScriptEngineAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(EngineFactory.class)
  public EngineFactory engineCache() {
    return new GraalJSEngineCache();
  }
}
