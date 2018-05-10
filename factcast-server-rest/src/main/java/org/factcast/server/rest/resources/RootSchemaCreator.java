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
package org.factcast.server.rest.resources;

import static com.mercateo.common.rest.schemagen.util.OptionalUtil.collect;

import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;
import lombok.val;

/**
 * helper class creating the hyper schema for the response of the root resource
 * 
 *
 */
@Component
@AllArgsConstructor
class RootSchemaCreator {
    private final HyperSchemaCreator hyperSchemaCreator;

    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    private final LinkFactory<FactsTransactionsResource> transactionsLinkFactory;

    ObjectWithSchema<Void> forRoot() {
        val getFactsLink = factsResourceLinkFactory.forCall(FactsRel.FACT_IDS, r -> r
                .getServerSentEvents(null));

        val getFullFactsLink = factsResourceLinkFactory.forCall(FactsRel.FULL_FACTS, r -> r
                .getServerSentEventsFull(null));
        val createLink = transactionsLinkFactory.forCall(FactsRel.CREATE_TRANSACTIONAL, r -> r
                .newTransaction(null));

        return hyperSchemaCreator.create(null, collect(getFactsLink, getFullFactsLink, createLink));
    }
}
