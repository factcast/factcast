/**
 * Copyright © 2018 Mercateo AG (http://www.mercateo.com)
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
package org.factcast.store.pgsql;

import org.factcast.store.pgsql.internal.PGFactStoreInternalConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration to include in order to use a PGFactStore
 *
 * just forwards to {@link PGFactStoreInternalConfiguration}, so that IDEs can
 * still complain about internal references.
 *
 * @author uwe.schaefer@mercateo.com
 */
@Configuration
@EnableConfigurationProperties
@Import(PGFactStoreInternalConfiguration.class)
@ComponentScan
public class PGFactStoreConfiguration {
}
