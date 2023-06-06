/*
 * Copyright © 2017-2023 factcast.org
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
package org.factcast.store.internal.filter.blacklist;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@SuppressWarnings("DefaultAnnotationParam")
@ConfigurationProperties(prefix = "factcast.blacklist")
@Slf4j
@Data
@Validated
@Accessors(fluent = false)
public final class BlacklistConfigurationProperties implements InitializingBean {

  BlacklistType type = BlacklistType.POSTGRES;

  String location = "classpath:blacklist.json";

  @Override
  public void afterPropertiesSet() throws Exception {
    log.info("Blacklist is retrieved from " + type.toString());
  }
}
