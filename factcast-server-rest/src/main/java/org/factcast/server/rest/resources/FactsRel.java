package org.factcast.server.rest.resources;

import com.mercateo.common.rest.schemagen.link.relation.RelType;
import com.mercateo.common.rest.schemagen.link.relation.Relation;
import com.mercateo.common.rest.schemagen.link.relation.RelationContainer;

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
