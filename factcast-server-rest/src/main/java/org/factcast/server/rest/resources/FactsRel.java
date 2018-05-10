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

import com.mercateo.common.rest.schemagen.link.relation.RelType;
import com.mercateo.common.rest.schemagen.link.relation.Relation;
import com.mercateo.common.rest.schemagen.link.relation.RelationContainer;

/**
 * enumeration for all possible custom relations on facts and transactions
 * 
 * @author joerg_adler
 *
 */
public enum FactsRel implements RelationContainer {
    FACT_IDS {
        @Override
        public Relation getRelation() {
            return Relation.of("http://rels.factcast.org/fact-ids", RelType.OTHER);
        }
    },
    FULL_FACTS {
        @Override
        public Relation getRelation() {
            return Relation.of("http://rels.factcast.org/full-facts", RelType.OTHER);
        }
    },
    CREATE_TRANSACTIONAL {
        @Override
        public Relation getRelation() {
            return Relation.of("http://rels.factcast.org/create-transactional", RelType.OTHER);
        }
    },
}
