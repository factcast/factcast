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
package org.factcast.itests.highlevel;

import java.util.UUID;

import org.factcast.core.Fact;
import org.factcast.core.spec.Specification;
import org.factcast.core.util.FactCastJson;
import org.factcast.highlevel.EventPojo;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Specification(ns = "test")
public class TestAggregateWasIncremented implements EventPojo {
    UUID aggregateId;

    @Override
    // TODO needs to be moved
    public Fact toFact(UUID randomUUID) {
        return Fact.builder()
                .id(randomUUID)
                .ns("test")
                .type(TestAggregateWasIncremented.class.getSimpleName())
                .aggId(aggregateId)
                .build(FactCastJson.writeValueAsString(this));
    }
}
