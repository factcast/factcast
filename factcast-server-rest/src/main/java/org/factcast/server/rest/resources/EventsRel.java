package org.factcast.server.rest.resources;

import com.mercateo.common.rest.schemagen.link.relation.RelType;
import com.mercateo.common.rest.schemagen.link.relation.Relation;
import com.mercateo.common.rest.schemagen.link.relation.RelationContainer;

public enum EventsRel implements RelationContainer {
	EVENTS {
		@Override
		public Relation getRelation() {
			return Relation.of("events", RelType.OTHER);
		}
	},
	CREATE_TRANSACTIONAL {
		@Override
		public Relation getRelation() {
			return Relation.of("create-transactional", RelType.OTHER);
		}
	},
}
