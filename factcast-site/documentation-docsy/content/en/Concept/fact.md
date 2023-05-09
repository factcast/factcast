---

title: "The Anatomy of a Fact"
linkTitle: "Anatomy of a Fact"
weight: 10
type: docs
----------

## Facts

FactCast is centered around _Facts_. We say Facts instead of Events, because Event has become a blurry term that could mean any number of things from a simple `onWhatNot() ` call handled by an Event-Loop to a `LegalContractCreated` with any flavor of semantics.

We decided to use the term Fact over Domain-Event because we want to highlight the notion of an Event being an immutable thing that, once it is published, became an observable Fact.

Obviously, a Fact is history and cannot be changed, after it happened. This is one of the cornerstones of EventSourcing and provides us with Facts being **immutable**, which plays an important role when it comes to caching.

Facts consist of two JSON documents: Header and Payload.

### The Header

consists of:

- a **required** Fact-Id 'id' of type UUID
- a **required** namespace 'ns' of type String
- an optional set of aggregateIds 'aggId' of type array of UUIDs
- an optional (but mostly used) Fact-Type 'type' of type String
- an optional Object 'meta' containing any number of key-value pairs, where the values are Strings
- any additional information you want to put in a Fact Header

JSON-Schema:

```json
{
	"$schema": "http://json-schema.org/draft-04/schema#",
	"definitions": {},
	"id": "http://docs.factcast.org/example/fact.json",
	"properties": {
		"id": {
			"id": "/properties/id",
			"type": "string",
			"pattern": "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
		},
		"aggIds": {
			"id": "/properties/aggIds",
			"type": "array",
			"items": {
				"id": "/properties/aggIds/items",
				"type": "string",
				"pattern": "^[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}$"
			}
		},
		"ns": {
			"id": "/properties/ns",
			"type": "string"
		},
		"type": {
			"id": "/properties/type",
			"type": "string"
		},
		"meta": {
			"id": "/properties/meta",
			"type": "object",
			"additionalProperties": {
				"type": "string",
				"description": "Some string values"
			}
		}
	},
	"type": "object",
	"additionalProperties": {
		"type": "object"
	},
	"required": ["id", "ns"]
}
```

### The Metadata Object

The Meta-Data Object is optional and consist of key:value pairs. The reason for it is that implementations can filter facts on certain attributes efficiently (without indexing the whole Fact payload).
When a fact is read from FactCast, it is guaranteed to have two field set in the Meta-Data object of the header:

| Attribute | Type         | Semantics                                                                                    |
|:----------|:-------------|:---------------------------------------------------------------------------------------------|
| \_ser     | long / int64 | unique serial number for the fact, that determines a before/after relationship between facts |
| \_ts      | long / int64 | timestamp in milliseconds, when this fact was published to factcast.                         |

As you can see, all meta-data attributes prefixed with "\_" are supposed to be server created, so please do not use an "\_" prefix yourself.

### The Payload

The payload has no constraints other than being a valid JSON document.

please, see GRPC docs for further details.
