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

import java.util.Optional;

import javax.ws.rs.core.Link;

import org.springframework.stereotype.Component;

import com.mercateo.common.rest.schemagen.link.LinkFactory;
import com.mercateo.common.rest.schemagen.link.relation.Rel;
import com.mercateo.common.rest.schemagen.types.HyperSchemaCreator;
import com.mercateo.common.rest.schemagen.types.ObjectWithSchema;

import lombok.AllArgsConstructor;

/**
 * helper class for creating schemas for facts
 * 
 *
 */
@Component
@AllArgsConstructor
class FactsSchemaCreator {

    private final LinkFactory<FactsResource> factsResourceLinkFactory;

    private final HyperSchemaCreator hyperSchemaCreator;

    ObjectWithSchema<FactJson> forFactWithId(FactJson returnValue) {
        Optional<Link> selfLink = factsResourceLinkFactory.forCall(Rel.SELF, r -> r.getForId(
                returnValue.header().id().toString()));

        return hyperSchemaCreator.create(returnValue, collect(selfLink));
    }
}
