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
package org.factcast.store.internal.jdbc;

import java.util.Arrays;
import java.util.Properties;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.PGProperty;
import org.postgresql.ds.common.BaseDataSource;
import org.springframework.beans.factory.config.BeanPostProcessor;

@Slf4j
public final class PgBatchInsertRewriteConfigurer implements BeanPostProcessor {
  private static final String PROPERTY = PGProperty.REWRITE_BATCHED_INSERTS.getName();

  // A regular post-processor runs after Boot's priority-ordered configuration property binding,
  // but before initialization methods can open the pool. Never borrow a connection here.
  @Override
  public Object postProcessBeforeInitialization(Object bean, String beanName) {
    if (bean instanceof org.apache.tomcat.jdbc.pool.DataSource ds) {
      if (ds.getDataSource() != null || ds.getDataSourceJNDI() != null || !isPostgres(ds.getUrl()))
        return bean;
      if (ds.getPoolSize() > 0) {
        log.warn("Cannot configure batch insert rewriting on an already-started Tomcat pool");
        return bean;
      }
      ds.setUrl(rewriteUrl(ds.getUrl()));
      ds.setDbProperties(withRewrite(ds.getDbProperties()));
    } else if (bean instanceof BaseDataSource ds) {
      ds.setReWriteBatchedInserts(true);
    }
    return bean;
  }

  private static boolean isPostgres(String url) {
    return url != null && url.startsWith("jdbc:postgresql:");
  }

  private static Properties withRewrite(Properties original) {
    Properties properties = new Properties();
    if (original != null) {
      properties.putAll(original);
      // Preserve inherited string properties as well as directly configured entries.
      original
          .stringPropertyNames()
          .forEach(key -> properties.setProperty(key, original.getProperty(key)));
    }
    PGProperty.REWRITE_BATCHED_INSERTS.set(properties, true);
    return properties;
  }

  private static String rewriteUrl(String url) {
    int query = url.indexOf('?');
    if (query < 0) return url;
    // URL properties override driver properties. Replace every occurrence without rebuilding
    // or decoding unrelated URL values, which may contain escaped credentials or options.
    return url.substring(0, query + 1)
        + Arrays.stream(url.substring(query + 1).split("&", -1))
            .map(part -> part.startsWith(PROPERTY + "=") ? PROPERTY + "=true" : part)
            .collect(Collectors.joining("&"));
  }
}
