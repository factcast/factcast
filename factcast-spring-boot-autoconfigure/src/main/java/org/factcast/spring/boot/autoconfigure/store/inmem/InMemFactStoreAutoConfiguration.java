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
package org.factcast.spring.boot.autoconfigure.store.inmem;

import org.factcast.core.store.FactStore;
import org.factcast.store.inmem.InMemFactStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import lombok.extern.slf4j.Slf4j;

/**
 * Configuration to include in order to use an InMemStore
 *
 * @author uwe.schaefer@mercateo.com, joerg.adler@mercateo.com
 */
@SuppressWarnings("deprecation")
@Configuration
@ConditionalOnClass(InMemFactStore.class)
@Slf4j
public class InMemFactStoreAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @Primary
    public FactStore factStore() {
        log.warn("");
        log.warn(
                "***********************************************************************************************************");
        log.warn(
                "* You are using an inmem-impl of a FactStore. This implementation is for quick testing ONLY and will fail *");
        log.warn(
                "*   with OOM if you load it with a significant amount of Facts.                                           *");
        log.warn(
                "***********************************************************************************************************");
        log.warn("");
        return new InMemFactStore();
    }
}
